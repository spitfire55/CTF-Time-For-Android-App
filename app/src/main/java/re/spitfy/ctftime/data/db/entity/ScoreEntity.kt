package re.spitfy.ctftime.data.db.entity

import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.ForeignKey.CASCADE

@Entity(
        tableName = "score",
        foreignKeys = [ForeignKey(
                entity = TeamEntity::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("teamId"),
                onDelete = CASCADE
        )]
)
data class ScoreEntity (
    val year: String,
    var points: Double,
    var rank: Int,
    val teamId: Int
)