package com.oracle.ee.spentanalyser.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merchant_mappings")
data class MerchantMappingEntity(
    @PrimaryKey val alias: String,
    val normalizedName: String
)
