import java.net.InetSocketAddress
import java.nio.file.Path

import scala.annotation.experimental

import org.http4s.ember.client.EmberClientBuilder

import com.sun.net.httpserver.*

import _root_.io.github.quafadas.dairect.*
import _root_.io.github.quafadas.dairect.ChatGpt.*

import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*
import cats.syntax.option.catsSyntaxOptionId
import fs2.*
import fs2.io.process.ProcessBuilder
import smithy4s.deriving.{*, given}

lazy val tsImpl = new TsCode {}

@experimental
object Facade extends IOApp.Simple:

  lazy val logFile = fs2.io.file.Path("log.txt")
  lazy val chatGpt = ChatGpt.defaultAuthLogToFile(logFile)

  lazy val tsImpl = new TsCode() {}

  lazy val params: ChatGptConfig = ChatGptConfig(
    model = "gpt-4o",
    temperature = Some(0.5)
  )

  override def run: IO[Unit] =

    val startMessages = List(
      AiMessage.system(
        """|You are a typescript expert. You are given the ability to read and write files, and start a server in a given directory."""
      ),
      AiMessage.user(
        """|Create a temporary directoryy.
          |Create a .ts file in that directory, containg a program to demonstrate the basics of three JS. Import three.js as an ESM module using the following import.
          |https://cdn.jsdelivr.net/npm/three@0.172.0/build/three.module.js
          |
          |Install the type declartions usiung the tool provided.
          |
          |Create an index.html file which references the compiled .ts file (i.e. has a .js extension) ready to be served.
          |
          | Once the code is written, the "serveTsDir" function will invoke tsc and serve the result.
          | Call the tool to check with your human, that the output is as expected. If successul invoke playwright to take a screenshot of the result, which will be sent to you.
          | This is your reference picture.
          |
          |
        """
      )
    )

    chatGpt
      .use { bot =>
        Agent.startAgent(bot, startMessages, params, API[TsCode].liftService(tsImpl))
      }
      .flatTap { msgs =>
        IO.println("finished") >>
          IO.println(msgs.mkString("\n"))
      }
      .void

  end run

end Facade
