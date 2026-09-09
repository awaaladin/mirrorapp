package com.mirror.backend

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

/** A single Users table is all this MVP backend needs - one row per account. */
object Users : IntIdTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAtEpochMillis = long("created_at_epoch_millis")
}

/** File-based SQLite so accounts survive server restarts, with zero external DB to install. */
fun initDatabase() {
    Database.connect("jdbc:sqlite:mirror-backend.db", driver = "org.sqlite.JDBC")
    transaction {
        SchemaUtils.create(Users)
    }
}
