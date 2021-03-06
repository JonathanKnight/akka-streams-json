package com.qmee.akka.stream.support

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.qmee.akka.json.stream.JsonStreamParser
import io.circe.CursorOp.DownField
import io.circe._
import org.typelevel.jawn.AsyncParser

trait CirceStreamSupport {

  def decode[A: Decoder]: Flow[ByteString, A, NotUsed] =
    JsonStreamParser.flow[Json](CirceSupportParser.facade).map(decodeJson[A])

  def decode[A: Decoder](mode: AsyncParser.Mode): Flow[ByteString, A, NotUsed] =
    JsonStreamParser.flow[Json](mode)(CirceSupportParser.facade).map(decodeJson[A])

  def encode[A](implicit A: Encoder[A], P: Printer = Printer.noSpaces): Flow[A, String, NotUsed] =
    Flow[A].map(a => P.pretty(A(a)))

  case class JsonParsingException(df: DecodingFailure, cursor: HCursor)
      extends Exception(errorMessage(df.history, cursor, df.message), df)

  private[qmee] def decodeJson[A](json: Json)(implicit decoder: Decoder[A]): A = {
    val cursor = json.hcursor
    decoder(cursor) match {
      case Right(e) => e
      case Left(f)  => throw JsonParsingException(f, cursor)
    }
  }

  private[this] def errorMessage(hist: List[CursorOp], cursor: HCursor, typeHint: String) = {
    val ac = cursor.replay(hist)
    if (ac.failed && lastWasDownField(hist)) {
      s"The field [${CursorOp.opsToPath(hist)}] is missing."
    } else {
      s"Could not decode [${ac.focus.getOrElse(Json.Null)}] at [${CursorOp.opsToPath(hist)}] as [$typeHint]."
    }
  }

  private[this] def lastWasDownField(hist: List[CursorOp]) = hist.headOption match {
    case Some(DownField(_)) => true
    case _                  => false
  }
}

object CirceStreamSupport extends CirceStreamSupport