package info.plateaukao.einkbro.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "favicons")
data class FaviconInfo(
   @PrimaryKey
   val domain: String,
   
   var icon: ByteArray?
)