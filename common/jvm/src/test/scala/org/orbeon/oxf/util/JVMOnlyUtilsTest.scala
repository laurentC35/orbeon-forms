/**
 * Copyright (C) 2012 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util

import org.orbeon.oxf.util.IOUtils._
import org.orbeon.oxf.util.PathUtils._
import org.scalatest.FunSuite

class JVMOnlyUtilsTest extends FunSuite {

  test("UseAndClose") {

    val closable = new {
      var closed = false
      def close() = closed = true
      def value = 42
    }

    assert(42 === useAndClose(closable)(_.value))
    assert(closable.closed)

    assert(null eq useAndClose(null: {def close()})(identity))
  }

  test("RunQuietly") {
    assert(() === runQuietly(throw new RuntimeException))
  }

  test("EncodeSimpleQuery") {

    val query1 = """p1=v11&p2=v21&p1=v12&p2=&p2=v23&p1="""
    assert(query1 === encodeSimpleQuery(decodeSimpleQuery(query1)))

    assert("name=%C3%89rik" === encodeSimpleQuery(Seq("name" → "Érik")))
  }
}