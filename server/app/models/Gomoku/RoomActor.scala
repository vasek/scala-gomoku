package models.Gomoku

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.libs.concurrent._
import play.api.libs.iteratee._
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object RoomActor {

  implicit val timeout = Timeout(1 second)

  val store = RoomStore

  lazy val default = Akka.system.actorOf(Props[RoomActor])

  def join(id: String): Future[(Iteratee[JsValue, _], Enumerator[JsValue])] = {

    (default ? Join(id)).map {

      case Connected(enumerator) =>
        val iteratee = Iteratee.foreach[JsValue] { event =>
          processEvent(id, event)
        }.map { _ =>
          default ! Quit(id)
        }
        (iteratee, enumerator)

      case CannotConnect(error) =>
        val iteratee = Done[JsValue, Unit]((), Input.EOF)
        val enumerator = Enumerator[JsValue](JsObject(Seq("error" -> JsString(error))))
          .andThen(Enumerator.enumInput(Input.EOF))
        (iteratee, enumerator)

    }
  }

  def processEvent(id: String, event: JsValue): Unit = {
    val action = (event \ "action").as[String]
    val player = (event \ "player").as[String]
    val text = (event \ "text").as[String]

    action match {
      case "login" => default ! Login(id, player)
      case "move" => default ! Move(id, player, text)
    }
  }

}

class RoomActor extends Actor {

  val (gameEnumerator, gameChannel) = Concurrent.broadcast[JsValue]

  def receive = {

    case Join(id) =>
      try {
        val room = RoomActor.store.get(id).addPlayer()
        RoomActor.store.set(id, room)
        sender ! Connected(gameEnumerator)
        self ! NotifyJoin(id, "")
      } catch {
        case e:TooMuchPlayersException => sender ! CannotConnect("Too much players")
      }

    case Login(id, username) =>
      val room = RoomActor.store.get(id).addPlayer(username)
      RoomActor.store.set(id, room)
      self ! NotifyJoin(id, username)
      if (room.players.size == 2) {
        self ! Start(id)
      }

    case Start(id) =>
      val room = RoomActor.store.get(id)
      notifyAll(id, "start", room.players.head)

    case Move(id, player, coord) =>
      val room = RoomActor.store.get(id)
      room.move(player, coord)
      room.getWinner(coord).map{ winner =>
        notifyAll(id, "win", winner, coord)
      }.getOrElse {
        notifyAll(id, "move", player, coord)
      }

    case NotifyJoin(id, player) =>
      notifyAll(id, "join", player)

    case Quit(id) =>
      RoomActor.store.remove(id)

  }

  def notifyAll(id: String, action: String, player: String, text: String = ""): Unit = {
    val message = JsObject(
      Seq(
        "room" -> JsString(id),
        "action" -> JsString(action),
        "player" -> JsString(player),
        "text" -> JsString(text)
      )
    )

    gameChannel.push(message)
  }

}

case class Join(id: String)
case class NotifyJoin(id: String, player: String)
case class Quit(id: String)

case class Login(id: String, username: String)
case class Start(id: String)
case class Move(id: String, player: String, coord: String)

case class Connected(enumerator: Enumerator[JsValue])
case class CannotConnect(message: String)
