/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kyuubi.engine.flink.result

import java.util

import scala.collection.JavaConverters._

import com.google.common.collect.Iterators
import org.apache.flink.table.api.{ResultKind, TableResult}
import org.apache.flink.table.catalog.Column
import org.apache.flink.types.Row

import org.apache.kyuubi.operation.{ArrayFetchIterator, FetchIterator}

case class ResultSet(
    resultKind: ResultKind,
    columns: util.List[Column],
    data: FetchIterator[Row],
    // null in batch mode
    // list of boolean in streaming mode,
    // true if the corresponding row is an append row, false if its a retract row
    changeFlags: Option[util.List[Boolean]]) {

  require(resultKind != null, "resultKind must not be null")
  require(columns != null, "columns must not be null")
  require(data != null, "data must not be null")
  changeFlags.foreach { flags =>
    require(
      Iterators.size(data.asInstanceOf[util.Iterator[_]]) == flags.size,
      "the size of data and the size of changeFlags should be equal")
  }

  def getColumns: util.List[Column] = columns

  def getData: FetchIterator[Row] = data
}

/**
 * A set of one statement execution result containing result kind, columns, rows of data and change
 * flags for streaming mode.
 */
object ResultSet {

  def fromTableResult(tableResult: TableResult): ResultSet = {
    val schema = tableResult.getResolvedSchema
    // collect all rows from table result as list
    // this is ok as TableResult contains limited rows
    val rows = tableResult.collect.asScala.toArray
    builder.resultKind(tableResult.getResultKind)
      .columns(schema.getColumns)
      .data(rows)
      .build
  }

  def builder: Builder = new ResultSet.Builder

  class Builder {
    private var resultKind: ResultKind = _
    private var columns: util.List[Column] = _
    private var data: FetchIterator[Row] = _
    private var changeFlags: Option[util.List[Boolean]] = None

    def resultKind(resultKind: ResultKind): ResultSet.Builder = {
      this.resultKind = resultKind
      this
    }

    def columns(columns: Column*): ResultSet.Builder = {
      this.columns = columns.asJava
      this
    }

    def columns(columns: util.List[Column]): ResultSet.Builder = {
      this.columns = columns
      this
    }

    def data(data: FetchIterator[Row]): ResultSet.Builder = {
      this.data = data
      this
    }

    def data(data: Array[Row]): ResultSet.Builder = {
      this.data = new ArrayFetchIterator[Row](data)
      this
    }

    def changeFlags(changeFlags: util.List[Boolean]): ResultSet.Builder = {
      this.changeFlags = Some(changeFlags)
      this
    }

    def build: ResultSet = new ResultSet(resultKind, columns, data, changeFlags)
  }
}
