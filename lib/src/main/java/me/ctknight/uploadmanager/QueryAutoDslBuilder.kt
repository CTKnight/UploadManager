// Code generated by AutoDsl. Do not edit.
package me.ctknight.uploadmanager

import com.autodsl.annotation.AutoDslMarker
import kotlin.Boolean
import kotlin.LongArray
import kotlin.String
import kotlin.Unit
import kotlin.properties.Delegates

fun query(block: QueryAutoDslBuilder.() -> Unit): UploadManager.Query = QueryAutoDslBuilder().apply(block).build()

@AutoDslMarker
class QueryAutoDslBuilder() {
  var ids: LongArray by Delegates.notNull()

  var orderColumn: String? = null

  var ascOrder: Boolean? = null

  fun withIds(ids: LongArray): QueryAutoDslBuilder = this.apply { this.ids = ids}

  fun withOrderColumn(orderColumn: String?): QueryAutoDslBuilder = this.apply { this.orderColumn = orderColumn}

  fun withAscOrder(ascOrder: Boolean?): QueryAutoDslBuilder = this.apply { this.ascOrder = ascOrder}

  fun build(): UploadManager.Query = UploadManager.Query(ids, orderColumn, ascOrder)
}
