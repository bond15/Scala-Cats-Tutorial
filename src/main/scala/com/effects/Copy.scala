package src.main.scala.com.effects

import cats.effect._
import cats.implicits._
import java.io._

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = for {
    _ <- if(args.length < 2) IO.raiseError(new IllegalArgumentException("needs files"))
    else IO.unit
    orig = new File(args(0))
    dest = new File(args(1))
    count <- copy(orig,dest)
    _ <- IO(println(s"$count bytes"))
  } yield ExitCode.Success



  // goal, copy one file contents to another
  // use the resouruce
  def copy(origin: File, dest: File): IO[Long] =
    inputOutputStream(origin,dest).use {
      case (in,out) => transfer(in,out)
    }

  // initialize a transfer buffer, call recursive copier (transmit), return total bytes copied
  def transfer(origin: InputStream, dest: OutputStream): IO[Long] =
    for {
      buffer <- IO(new Array[Byte](1024*10))
      total  <- transmit(origin, dest, buffer, 0L)
    } yield total


  def transmit(origin: InputStream, dest: OutputStream, buff: Array[Byte], acc: Long): IO[Long] =
    for {
      amount <- IO(origin.read(buff,0,buff.size))
      count <- if(amount > -1)
        IO(dest.write(buff,0,amount)) >> transmit(origin,dest,buff, acc + amount)
        else
        IO.pure(acc)
    } yield count

  def inputStream(f: File): Resource[IO, FileInputStream] =
    Resource.make {
      IO(new FileInputStream(f)) // build
    } { inStream =>
      IO(inStream.close()).handleErrorWith(_ => IO.unit) // release
    }

  def outputStream(f: File): Resource[IO,FileOutputStream] =
    Resource.make {
      IO(new FileOutputStream(f))
    } { outStream =>
      IO(outStream.close()).handleErrorWith(_ => IO.unit)
    }

  //combines input and output resources in one context
  def inputOutputStream(in: File, out: File): Resource[IO, (InputStream,OutputStream)] =
    for {
      inStream <- inputStream(in)
      outStream <- outputStream(out)
    } yield (inStream,outStream)
}