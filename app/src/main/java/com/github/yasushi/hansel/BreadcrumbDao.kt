package com.github.yasushi.hansel

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query

@Dao
interface BreadcrumbDao {
    @Query("select * from breadcrumb")
    fun selectAll(): List<Breadcrumb>

    @Insert
    fun insert(breadcrumb: Breadcrumb)

    @Query("delete from breadcrumb where ts = :ts")
    fun delete(ts: Long)
}