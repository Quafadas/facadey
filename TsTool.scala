import java.net.InetSocketAddress
import java.nio.file.Path
import scala.compiletime.uninitialized
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
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import scala.util.chaining.*
import smithy4s.Blob

// object FilthyCacheConcept:
//   lazy val pw: Playwright = Playwright.create()
//   lazy val browserOpts = BrowserType.LaunchOptions().tap(_.headless = false)
//   lazy val browser = pw.webkit.launch(browserOpts)

// end FilthyCacheConcept

@hints(
  smithy.api.Documentation(
    "Local file and os operations, as well as typescript operations and the ability to start a local webserver"
  )
)
trait TsCode derives API:
  // private val fcc = FilthyCacheConcept

  /** Creates a temporary directory on the local file system.
    */
  def makeTempDir(dirPrefix: String): IO[String] =
    IO.println("Creating a temporary directory") >>
      IO.blocking {
        val outDir = os.temp.dir(deleteOnExit = false, prefix = dirPrefix).toString
        outDir.toString
      }

  def createOrOverwriteFileInDir(dir: String, fileName: String, contents: Option[String]): IO[String] =
    IO.println(s"Creating $dir/$fileName file in $dir called $fileName") >>
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
      .withWorkingDirectory(fs2.io.file.Path(projectPath))
      .spawn[IO]
      .map { proc =>
        proc.stdout.through(fs2.text.utf8.decode).evalTap(IO.println(_)).compile.drain
      }
      .use { proc =>
        proc >>
          IO.println(s"Installing typescript module type declarations for $forModule in $projectPath")
      }
      .map(_ => s"Installed typescript module type declarations for $forModule in $projectPath")

  // @hints(
  //   smithy.api.Documentation(
  //     "Takes a screencap of localhost:port and returns it as an image"
  //   )
  // )
  // def playwrightTakeImage(port: Int, outPath: String): IO[Blob] = IO {
  //   val page = fcc.browser.newPage().tap(_.navigate(s"http://localhost:$port"))
  //   val bytes = page.screenshot()
  //   Blob(bytes)
  // }
  // end playwrightTakeImage

  def compileTsDir(dir: String): IO[String] =

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

    IO.println(s"compile typescript $dir ") >>
      proc.use { p =>
        p.stdout.through(fs2.text.utf8.decode).evalTap(IO.println(_)).compile.string
      }

  end compileTsDir

  /** start a local webserver in a given directory
    */
  def serveDir(dir: String, port: Int): IO[String] =

    val serveIo = IO(
      SimpleFileServer
        .createFileServer(
          new InetSocketAddress(port),
          Path.of(dir),
          com.sun.net.httpserver.SimpleFileServer.OutputLevel.INFO
        )
        .start()
    )

    val openBrowser = IO.blocking {
      val desktop = java.awt.Desktop.getDesktop
      val uri = new java.net.URI(s"http://localhost:$port")
      desktop.browse(uri)
    }

    for
      _ <- serveIo
      _ <- openBrowser
      _ <- IO.println(s"Serving directory $dir")
    yield s"Server started on http://localhost:$port"
    end for

  end serveDir

  /** Use scala-cli to compile and link the code in a given directory
    */
  def compileAndLinkScalaJs(dir: String): IO[String] =
    val asPath = fs2.io.file.Path(dir)
    val scalaCliArgs = List(
      "--power",
      "package",
      "--js",
      "-f",
      "."
    )
    IO.println(s"compiling directory $dir with scala-cli ") >>
      ProcessBuilder(
        "scala-cli",
        scalaCliArgs
      ).withWorkingDirectory(asPath)
        .spawn[IO]
        .use { p =>
          val stdout = p.stdout
            .through(text.utf8.decode)
            .compile
            .string

          val stdError = p.stderr
            .through(text.utf8.decode)
            .compile
            .string

          stdout.both(stdError).map { (out, err) =>
            s"stdout: $out\nstderr: $err"
          }
        }
  end compileAndLinkScalaJs

end TsCode
