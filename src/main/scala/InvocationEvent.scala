import io.circe.Json

import java.time.Instant

case class InvocationEvent(
    payload: Json,
    awsRequestId: AwsRequestId,
    deadline: Option[Instant],
    invokedFunctionArn: Option[String],
    traceId: Option[String],
    clientContext: Option[String],
    cognitoIdentity: Option[String]
)

case class AwsRequestId(value: String) extends AnyVal
