// Copyright (c) 2013-2017 Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.specs2

import cats.effect.{ Async, IO }
import doobie.imports._
import doobie.util.analysis._
import doobie.util.pretty._
import doobie.util.pos.Pos
import org.specs2.mutable.Specification
import org.specs2.execute.Failure
import org.specs2.specification.core.{ Fragments, Fragment }
import org.specs2.specification.dsl.Online._
import org.specs2.specification.create.{ FormattingFragments => Format }
import scala.reflect.runtime.universe.TypeTag

/**
 * Module with a mix-in trait for specifications that enables checking of doobie `Query` and `Update` values.
 * {{{
 * // An example specification, taken from the examples project.
 * object AnalysisTestSpec extends Specification with AnalysisSpec {
 *
 *   // The transactor to use for the tests.
 *   val transactor = Transactor.fromDriverManager[IO](
 *     "org.postgresql.Driver",
 *     "jdbc:postgresql:world",
 *     "postgres", ""
 *   )
 *
 *   // Now just mention the queries. Arguments are not used.
 *   check(MyDaoModule.findByNameAndAge(null, 0))
 *   check(MyDaoModule.allWoozles)
 *
 * }
 * }}}
 */
object analysisspec {

  @deprecated("Use IOChecker.", "0.4.2")
  type AnalysisSpec = IOChecker

  trait Checker[M[_]] { this: Specification =>

    // Effect type, required instances, unsafe run
    implicit val M: Async[M]
    def unsafeRunSync[A](ma: M[A]): A

    def transactor: Transactor[M]

    @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
    def check[A, B](q: Query[A, B])(implicit A: TypeTag[A], B: TypeTag[B]): Fragments =
      checkAnalysis(s"Query[${typeName(A)}, ${typeName(B)}]", q.pos, q.sql, q.analysis)

    @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
    def check[A](q: Query0[A])(implicit A: TypeTag[A]): Fragments =
      checkAnalysis(s"Query0[${typeName(A)}]", q.pos, q.sql, q.analysis)

    def checkOutput[A](q: Query0[A])(implicit A: TypeTag[A]): Fragments =
      checkAnalysis(s"Query0[${typeName(A)}]", q.pos, q.sql, q.outputAnalysis)

    @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
    def check[A](q: Update[A])(implicit A: TypeTag[A]): Fragments =
      checkAnalysis(s"Update[${typeName(A)}]", q.pos, q.sql, q.analysis)

    @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
    def check(q: Update0): Fragments =
      checkAnalysis(s"Update0", q.pos, q.sql, q.analysis)

    private def checkAnalysis(typeName: String, pos: Option[Pos], sql: String, analysis: ConnectionIO[Analysis]): Fragments =
      // continuesWith is necessary to make sure the query doesn't run too early
      s"\n$typeName defined at ${loc(pos)}\n${sql.lines.map(s => "  " + s.trim).filterNot(_.isEmpty).mkString("\n")}" >> ok.continueWith {
        unsafeRunSync(M.attempt(transactor.trans(M).apply(analysis))) match {
          // We can't rely on mutable Specification DSL here!
          case Left(e) => indentBlock(Seq(
            "SQL Compiles and Typechecks" ! failure(formatError(e.getMessage))
          ))
          case Right(a) => indentBlock(
            ("SQL Compiles and Typechecks" ! ok) +:
              a.paramDescriptions.map{ case (s, es) => s ! assertEmpty(es, pos) } ++:
              a.columnDescriptions.map{ case (s, es) => s ! assertEmpty(es, pos) }
          )
        }
      }

    private def loc(f: Option[Pos]): String =
      f.map(f => s"${f.file}:${f.line}").getOrElse("(source location unknown)")

    private def assertEmpty(es: List[AlignmentError], f: Option[Pos]) =
      if (es.isEmpty) (f, success)._2 // use f to stop warning ... todo, use it!
      else new Failure(es.map(formatAlignmentError).mkString("\n"), "", Nil)

    private val packagePrefix = "\\b[a-z]+\\.".r

    @SuppressWarnings(Array("org.wartremover.warts.ToString"))
    private def typeName[A](tag: TypeTag[A]): String =
      packagePrefix.replaceAllIn(tag.tpe.toString, "")

    private def formatAlignmentError(e: AlignmentError): String =
      formatError(e.msg)

    private def formatError(s: String): String =
      (wrap(80)(s) match {
        case s :: ss => (s"x " + s) :: ss.map("  " + _)
        case Nil => Nil
      }).mkString("\n")

    private def indentBlock(fs: Seq[Fragment]): Fragments =
      // intersperse fragments with newlines, and indent them.
      // This differs from standard version (FragmentsDsl.fragmentsBlock()) in
      // that any failure gets properly indented, too.
      Fragments.empty
        .append(Format.t)
        .append(fs.flatMap(Seq(Format.br, _)))
        .append(Format.bt)
  }

  /** Implementation of Checker[IO] */
  trait IOChecker extends Checker[IO] { this: Specification =>
    val M: Async[IO] = implicitly
    def unsafeRunSync[A](ma: IO[A]) = ma.unsafeRunSync
  }

}
