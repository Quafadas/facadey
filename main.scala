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

  lazy val logFile = fs2.io.file.Path("ai.log")
  lazy val chatGpt = ChatGpt.defaultAuthLogToFile(logFile)

  lazy val tsImpl = new TsCode() {}

  lazy val params: ChatGptConfig = ChatGptConfig(
    model = "gpt-4o",
    temperature = Some(0.5)
  )

  override def run: IO[Unit] =

    val startMessagesTs = List(
      AiMessage.system(
        """|You are a frontend expert that uses both typescript and scalaJS with a focus on three.js. You write clear, diligent and well documented code, paying particular attention to making sure that
           |type ascriptions (even for local const variables) are correct.
           |""".stripMargin
      ),
      AiMessage.user(
        """|Create a temporary directory.
          |
          |Install the type declartions in that directory for three.js using the installTypescriptModuleTypDeclarations tool provided.
          |
          |Create a .ts file in the temp directory, containg a program to show a simple stickman shape in three JS.
          |
          |Compile the temp directory, using the compileTsDir tool provided.
          |
          |If successful create an index.html file which references the compiled .ts file (i.e. has a .js extension) ready to be served. As part of the index.html file, include
          |an ESModule map which maps `three` to https://cdn.jsdelivr.net/npm/three@0.172.0/build/three.module.js, i.e. resolves it from a CDN
          |
          |Once the code is compiled sucessfuly, call the "serveDir" function to serve the result on port 3001.
          |Call the tool to check with your human, that the output is as expected, if it is not, continue with the following loop taking into account human feedback;
          |
          |1. Rewrite the typesript file in the temp directory.
          |2. Compile the temp directory.
          |3. Ask the human if the output is correct (the server doesn't need to be restarted)
          |
          |Our goal, is to rewrite this code in scalaJS. To do this, firstly, create a new temporary directory.
          |Rewrite the code from typescript above, to scala JS (including a facadde for the part of three.js we used). The first lines of the scala file, should contains the following
          |```
          |//> using scala 3.6.2
          |//> using platform js
          |//> using dep org.scala-js::scalajs-dom::2.8.0
          |//> using jsModuleKind es
          |```
          |Your three JS facade, will begin with the following;
          |@JSImport(
          | "https://cdn.jsdelivr.net/npm/three@0.172.0/build/three.module.js",
          | JSImport.Namespace
          |)
          |Which will resolve the dependancy from CDN when served in browser.
          |Call the "compileAndLinkScalaJs" function to compile the code, continually iterating on your solution until it is successful.
          |
          |Next, write an index.html file into the temp directory, which references the compiled scala.js file.
          |
          |Finally, call the "serveDir" function for the scala temporary directory on port 3002. Check with the human to make sure the result is as expected and adjust the scala code as necessary.
          |
          |Once the scalaJS code displays the same picture as the typescript code, you are finished. Provide a nicely formatted message which tells the user the location of
          |bot the typescript code, and scalaJS code.
          |
          |Begin!
        """
      )
    )

    chatGpt
      .use { bot =>
        Agent.startAgent(bot, startMessagesTs, params, API[TsCode].liftService(tsImpl))
      }
      .flatTap { msgs =>
        IO.println("finished") >>
          IO.println(msgs.mkString("\n"))
      }
      .void

  end run

end Facade
