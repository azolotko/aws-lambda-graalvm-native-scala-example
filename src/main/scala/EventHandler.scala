import cats.Applicative
import cats.syntax.applicative._

object EventHandler {
  def handle[F[_]: Applicative](invocationEvent: InvocationEvent): F[String] =
    invocationEvent.payload.spaces2.pure
}
