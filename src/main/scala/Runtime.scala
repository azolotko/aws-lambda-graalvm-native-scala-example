import cats.data.EitherT
import cats.effect._
import cats.syntax.all._
import io.circe.Json
import io.circe.literal.JsonStringContext
import org.http4s.Method.{GET, POST}
import org.http4s.Status.Successful
import org.http4s._
import org.http4s.circe._
import org.http4s.client._
import org.http4s.client.jdkhttpclient.JdkHttpClient
import org.http4s.util.CaseInsensitiveString

import java.time.Instant
import scala.util.control.NoStackTrace

object Runtime extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    IO.fromOption(
        sys.env
          .get("AWS_LAMBDA_RUNTIME_API")
          .flatMap(s => Uri.fromString(s"http://$s").toOption)
      )(
        new RuntimeException("AWS_LAMBDA_RUNTIME_API is undefined or malformed")
      )
      .flatMap { runtimeUri =>
        JdkHttpClient
          .simple[IO]
          .flatMap(invocationLoop(_, runtimeUri))
      }

  private def invocationLoop(
      client: Client[IO],
      runtimeUri: Uri
  ): IO[Nothing] = {
    val invocationUri = runtimeUri / "2018-06-01" / "runtime" / "invocation"

    val nextUri = invocationUri / "next"

    def responseUri(awsRequestId: AwsRequestId) =
      invocationUri / awsRequestId.value / "response"

    def errorUri(awsRequestId: AwsRequestId) =
      invocationUri / awsRequestId.value / "error"

    invocationNext[IO](client, nextUri)
      .flatMap(invocationEvent =>
        EventHandler
          .handle[IO](invocationEvent)
          .flatMap(handlerResponse =>
            invocationResponse[IO](
              client,
              responseUri(invocationEvent.awsRequestId),
              handlerResponse
            ).handleErrorWith(e =>
              invocationError[IO](
                client,
                errorUri(invocationEvent.awsRequestId),
                e.getClass.getSimpleName,
                e.getMessage
              )
            )
          )
      )
      .foreverM
  }

  case object MissingAwsRequestId extends RuntimeException with NoStackTrace

  private def invocationNext[F[_]: Sync: EntityDecoder[*[_], Json]](
      client: Client[F],
      uri: Uri
  ): F[InvocationEvent] =
    client
      .run(Request[F](method = GET, uri = uri))
      .use {
        case Successful(response) =>
          val headers = response.headers
          EntityDecoder[F, Json]
            .decode(response, strict = true)
            .flatMap { payload =>
              EitherT
                .fromOption(awsRequestId(headers), MissingAwsRequestId)
                .leftWiden[Throwable]
                .map(requestId =>
                  InvocationEvent(
                    payload,
                    requestId,
                    deadline(headers),
                    invokedFunctionArn(headers),
                    traceId(headers),
                    clientContext(headers),
                    cognitoIdentity(headers)
                  )
                )
            }
            .rethrowT

        case failedResponse =>
          UnexpectedStatus(failedResponse.status).raiseError
      }

  private def invocationResponse[F[_]](
      client: Client[F],
      uri: Uri,
      response: String
  ): F[Status] =
    client.status(Request[F](method = POST, uri).withEntity(response))

  private def invocationError[F[_]: EntityEncoder[*[_], Json]](
      client: Client[F],
      uri: Uri,
      errorType: String,
      errorMessage: String
  ): F[Status] =
    client
      .status(
        Request[F](
          method = POST,
          uri,
          headers = Headers.of(
            Header("Lambda-Runtime-Function-Error-Type", "Unhandled")
          )
        ).withEntity(
          json"""{"errorType": $errorType, "errorMessage": $errorMessage}"""
        )
      )

  private def awsRequestId(headers: Headers): Option[AwsRequestId] =
    headerString(headers, "Lambda-Runtime-Aws-Request-Id")
      .map(AwsRequestId)

  private def deadline(headers: Headers): Option[Instant] =
    headerString(headers, "Lambda-Runtime-Deadline-Ms")
      .map(_.toLong)
      .map(Instant.ofEpochMilli)

  private def invokedFunctionArn(headers: Headers): Option[String] =
    headerString(headers, "Lambda-Runtime-Invoked-Function-Arn")

  private def traceId(headers: Headers): Option[String] =
    headerString(headers, "Lambda-Runtime-Trace-Id")

  private def clientContext(headers: Headers): Option[String] =
    headerString(headers, "Lambda-Runtime-Client-Context")

  private def cognitoIdentity(headers: Headers): Option[String] =
    headerString(headers, "Lambda-Runtime-Cognito-Identity")

  private def headerString(headers: Headers, name: String): Option[String] =
    headers.get(CaseInsensitiveString(name)).map(_.value)
}
