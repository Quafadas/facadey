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

@hints(
  smithy.api.Documentation(
    "Local file and os operations, as well as typescript operations and the ability to start a local webserver"
  )
)
trait TsCode derives API:

  /** Creates a temporary directory on the local file system.
    */
  def makeTempDir(dirPrefix: String): IO[String] =
    IO.println("Creating a temporary directory") >>
      IO.blocking {
        val outDir = os.temp.dir(deleteOnExit = false, prefix = dirPrefix).toString
        outDir.toString
      }

  def createOrOverwriteFileInDir(dir: String, fileName: String, contents: Option[String]): IO[String] =
    IO.println(s"Creating a file in $dir called $fileName") >>
      IO.blocking {
        val filePath = os.Path(dir) / fileName
        os.write.over(filePath, contents.getOrElse(""))
        filePath.toString
      }

  def readTextFile(filePath: String): IO[String] =
    IO.println(s"Reading file $filePath") >>
      IO.blocking {
        os.read(os.Path(filePath))
      }

  def askForHelp(question: String): IO[String] =
    for
      _ <- Console[IO].println(s"I need guidance with: $question")
      n <- Console[IO].readLine
    yield n
  // override def compileTypescript_tsc(dir: String): IO[String] =
  //   println(s"called compileTypescript_tsc with $dir")
  //   val proc = ProcessBuilder("tsc", dir).spawn[IO]()
  //   proc.
  // end compileTypescript_tsc

  def installTypescriptModuleTypDeclarations(projectPath: String, forModule: String): IO[String] =
    ProcessBuilder("npm", List("install", "--save-dev", s"@types/$forModule"))
      .spawn[IO]
      .map { proc =>
        proc.stdout.through(fs2.text.utf8.decode).evalTap(IO.println(_)).compile.drain
      }
      .use { _ =>
        IO.println(s"Installing typescript module type declarations for $forModule in $projectPath")
      }
      .map(_ => s"Installed typescript module type declarations for $forModule in $projectPath")

  /** start a local webserver in a given directory
    */
  def serveTsDir(dir: String, port: Int): IO[String] =
    println(s"called serveTsDir with $dir on $port")
    val asPath = fs2.io.file.Path(dir)

    val listTsFiles = fs2.io.file
      .Files[IO]
      .list(asPath)
      .filter(_.toString.endsWith(".ts"))
      .map(_.toString)
      .compile
      .toList
      .toResource

    val proc =
      listTsFiles.flatMap { tsFiles =>
        ProcessBuilder("tsc", tsFiles ++ List("--module", "esnext", "--target", "esnext"))
          .withWorkingDirectory(asPath)
          .spawn[IO]
      }

    val runtsc = proc.use { p =>
      println("tsc output")
      p.stdout.through(fs2.text.utf8.decode).evalTap(IO.println(_)).compile.drain
    }

    val serveIo = IO(
      SimpleFileServer
        .createFileServer(
          new InetSocketAddress(port),
          Path.of(dir),
          com.sun.net.httpserver.SimpleFileServer.OutputLevel.VERBOSE
        )
        .start()
    )

    runtsc.flatMap(_ => serveIo.map(_ => "Server started"))

  end serveTsDir

end TsCode
