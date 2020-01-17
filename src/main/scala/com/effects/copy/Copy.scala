package com.effects.copy

import java.io._

import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.implicits._

object Main {//extends IOApp {

  /*override def run(args: List[String]): IO[ExitCode] = for {
    _ <- if(args.length < 2) IO.raiseError(new IllegalArgumentException("needs files"))
    else IO.unit
    orig = new File(args(0))
    dest = new File(args(1))
    count <- copy[IO](orig,dest)
    _ <- IO(println(s"$count bytes"))
  } yield ExitCode.Success*/

  // goal, copy one file contents to another
  // use the resouruce
  def copy[F[_]: Concurrent](origin: File, dest: File): F[Long] =
    for {
      guard <- Semaphore[F](1)
      count <- inputOutputStream(origin, dest, guard).use {
        case (in, out) => guard.withPermit(transfer(in, out))
      }
    } yield count

  // initialize a transfer buffer, call recursive copier (transmit), return total bytes copied
  def transfer[F[_]: Sync](origin: InputStream, dest: OutputStream): F[Long] =
    for {
      buffer <- Sync[F].delay(new Array[Byte](1024*10))
      total  <- transmit(origin, dest, buffer, 0L)
    } yield total


  def transmit[F[_]: Sync](origin: InputStream, dest: OutputStream, buff: Array[Byte], acc: Long): F[Long] =
    for {
      amount <- Sync[F].delay(origin.read(buff,0,buff.size))
      count <- if(amount > -1)
        Sync[F].delay(dest.write(buff,0,amount)) >> transmit(origin,dest,buff, acc + amount)
        else
        Sync[F].pure(acc)
    } yield count

  def inputStream[F[_]: Sync](f: File, guard: Semaphore[F]): Resource[F, FileInputStream] =
    Resource.make {
      Sync[F].delay(new FileInputStream(f)) // build
    } { inStream =>
      guard.withPermit(
      Sync[F].delay(inStream.close()).handleErrorWith(_ => Sync[F].pure(())))// release
    }

  def outputStream[F[_]: Sync](f: File, guard: Semaphore[F]): Resource[F,FileOutputStream] =
    Resource.make {
      Sync[F].delay(new FileOutputStream(f))
    } { outStream =>
      guard.withPermit(
      Sync[F].delay(outStream.close()).handleErrorWith(_ => Sync[F].pure(())))
    }

  //combines input and output resources in one context
  def inputOutputStream[F[_]: Sync](in: File, out: File, guard: Semaphore[F]): Resource[F, (InputStream,OutputStream)] =
    for {
      inStream <- inputStream(in,guard)
      outStream <- outputStream(out,guard)
    } yield (inStream,outStream)
}