// Copyright (c) 2013-2017 Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie
package hikari

import cats.effect.Async
import cats.implicits._
import com.zaxxer.hikari.HikariDataSource

object HikariTransactor {

  /** Constructs a program that yields an unconfigured `HikariTransactor`. */
  def initial[M[_]: Async]: M[HikariTransactor[M]] =
    Async[M].delay(Transactor.fromDataSource[M](new HikariDataSource))

  /** Constructs a program that yields a `HikariTransactor` from an existing `HikariDatasource`. */
  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def apply[M[_]: Async](hikariDataSource : HikariDataSource): HikariTransactor[M] =
    Transactor.fromDataSource[M](hikariDataSource)

  /** Constructs a program that yields a `HikariTransactor` configured with the given info. */
  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def apply[M[_]: Async](driverClassName: String, url: String, user: String, pass: String): M[HikariTransactor[M]] =
    for {
      _ <- Async[M].delay(Class.forName(driverClassName))
      t <- initial[M]
      _ <- t.configure { ds =>
        ds setJdbcUrl  url
        ds setUsername user
        ds setPassword pass
      }
    } yield t

}
