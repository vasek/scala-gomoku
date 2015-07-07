package models.Chat

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.google.common.html.HtmlEscapers
import play.api.Play.current
import play.api.libs.concurrent._
import play.api.libs.iteratee._
import play.api.libs.json._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object Room {

  implicit val timeout = Timeout(1 second)

  val store = UserStore

  lazy val default = Akka.system.actorOf(Props[Room])

  def join(username: String): Future[(Iteratee[JsValue, _], Enumerator[JsValue])] = {

    (default ? Join(username)).map {

      case Connected(user, enumerator) =>
        val iteratee = Iteratee.foreach[JsValue] { event =>
          talk(user, (event \ "text").as[String])
        }.map { _ =>
          default ! models.Chat.Quit(user)
        }
        (iteratee, enumerator)

      case CannotConnect(error) =>
        val iteratee = Done[JsValue, Unit]((), Input.EOF)
        val enumerator = Enumerator[JsValue](JsObject(Seq("error" -> JsString(error))))
          .andThen(Enumerator.enumInput(Input.EOF))
        (iteratee, enumerator)
    }

  }

  def talk(user: User, message: String): Unit = {
    default ! Talk(user, HtmlEscapers.htmlEscaper().escape(message).replace("\n", "<br/>"))
  }

}

class Room extends Actor {

  val (chatEnumerator, chatChannel) = Concurrent.broadcast[JsValue]

  def receive = {

    case Join(username) =>
      Room.store.get(username).map{ user =>
        println(Room.store.list())
        sender ! CannotConnect("This username is already used.")
      }.getOrElse {
        val user = User(username)
        Room.store.save(user)
        sender ! Connected(user, chatEnumerator)
        self ! NotifyJoin(user)
      }

    case NotifyJoin(user) =>
      notifyAll("join", user, "has entered the room")

    case Talk(user, message) =>
      notifyAll("talk", user, message)

    case Quit(user) =>
      Room.store.remove(user.username)
      notifyAll("quit", user, "has left the room")

  }

  def notifyAll(action: String, user: User, text: String) {
    val message = JsObject(
      Seq(
        "action" -> JsString(action),
        "user" -> JsString(user.username),
        "message" -> JsString(text),
        "members" -> JsArray(
          Room.store.list().map(user => JsString(user.username))
        )
      )
    )

    chatChannel.push(message)
  }

}

object UserStore {

  val users = mutable.Set.empty[String]

  def get(username: String): Option[User] = {
    if (users.contains(username)) Option(User(username)) else None
  }

  def list(): List[User] = {
    users.toList.map{ e => User(e) }
  }

  def save(user: User): Boolean = {
    users add user.username
  }

  def remove(username: String): Boolean = {
    users remove username
  }

}

case class User(username: String)

case class Join(username: String)
case class Talk(user: User, message: String)
case class Quit(user: User)
case class NotifyJoin(user: User)

case class Connected(user: User, enumerator: Enumerator[JsValue])
case class CannotConnect(message: String)
