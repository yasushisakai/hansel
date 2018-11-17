package com.github.yasushi.hansel

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase

@Database(entities = [Breadcrumb::class], version = 1, exportSchema = true)
abstract class BreadcrumbDatabase : RoomDatabase(){
    abstract fun breadcrumbDao() : BreadcrumbDao
}
