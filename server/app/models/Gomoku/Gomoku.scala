package models.Gomoku

import scala.collection.mutable
import scala.collection.immutable
import scala.util.Random

object RoomStore {

  val rooms = mutable.Map.empty[String, Room]

  def get(id: String): Room = {
    rooms.getOrElse(id, new Room)
  }

  def set(id: String, room: Room): Unit = {
    rooms(id) = room
  }

  def remove(id: String): Unit = {
    rooms.remove(id)
  }

}

class Room(val count: Int = 0, val players: immutable.Set[String] = immutable.Set.empty[String], var board: Board = new Board) {

  def addPlayer(): Room = {
    if (count < 2) {
      new Room(count + 1, players)
    } else {
      throw TooMuchPlayersException()
    }
  }

  def addPlayer(username: String): Room = {
    if (username != "")
      new Room(count, players + username)
    else this
  }

  def isFree: Boolean = {
    count != 2
  }

  def move(username: String, coord: String) = {
    val symbol: Char = if (username == players.head) 'x' else 'o'
    val row :: col :: xs = coord.split("-").toList.map(n => n.toInt)
    board.move(symbol, row, col)
  }

  def getWinner(lastMove: String): Option[String] = {
    val row :: col :: xs = lastMove.split("-").toList.map(n => n.toInt)

    board.getWinner(row, col).map{ winner =>
      if (winner == 'x') Some(players.head)
      else Some(players.tail.head)
    }.getOrElse {
      None
    }
  }

}

class Board(var board: Array[Array[Char]] = Array.ofDim[Char](19, 19)) {

  def move(symbol: Char, row: Int, col: Int): Unit = {
    board(row)(col) = symbol
  }

  def getWinner(row: Int, col: Int): Option[Char] = {
    def countInDirection(d: (Int, Int), symbol: Char): Int =
      ((1 to 4) takeWhile { i =>
        val x = row + d._1 * i
        val y = col + d._2 * i
        x >= 0 && x < 19 && y >= 0 && y < 19 &&
        board(x)(y) == symbol
      }).length

    val directions: List[(Int, Int)] = List((1, 0), (1, 1), (0, 1), (-1, 1))
    val symbol = board(row)(col)
    val winner = directions.exists { d =>
      1 + countInDirection(d, symbol) + countInDirection((-d._1, -d._2), symbol) >= 5
    }
    if (winner) Some(symbol)
    else None
  }

}

object Gomoku {

  def freeRoom(): String = {
    val freeRooms = RoomStore.rooms.filter(_._2.isFree)
    if (freeRooms.nonEmpty) {
      freeRooms.head._1
    } else {
      val ident = (Random.alphanumeric take 6).mkString
      RoomStore.get(ident)
      ident
    }
  }

}

case class TooMuchPlayersException() extends Exception