package de.lukas.recipebook

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Entity(tableName = "favorites")
data class Meal(@PrimaryKey val id: String, val name: String, val category: String,
    val area: String, val image: String, val instructions: String, val ingredients: String)

@Dao
interface MealDao {
    @Query("SELECT * FROM favorites ORDER BY name") fun observe(): Flow<List<Meal>>
    @Query("SELECT * FROM favorites WHERE id = :id") suspend fun find(id: String): Meal?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(meal: Meal)
    @Query("DELETE FROM favorites WHERE id = :id") suspend fun delete(id: String)
}
@Database(entities = [Meal::class], version = 1, exportSchema = false)
abstract class MealDatabase : RoomDatabase() {
    abstract fun meals(): MealDao
    companion object {
        @Volatile private var instance: MealDatabase? = null
        fun get(context: Context): MealDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, MealDatabase::class.java, "recipes.db")
                .build().also { instance = it }
        }
    }
}

object MealParser {
    fun parse(json: String): List<Meal> {
        val array = JSONObject(json).optJSONArray("meals") ?: return emptyList()
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            fun field(key: String) = item.optString(key).takeUnless { it == "null" }.orEmpty().trim()
            val ingredients = (1..20).mapNotNull { n ->
                field("strIngredient$n").takeIf { it.isNotBlank() }?.let { "${field("strMeasure$n")} $it".trim() }
            }.joinToString("\n")
            Meal(field("idMeal"), field("strMeal"), field("strCategory"), field("strArea"),
                field("strMealThumb"), field("strInstructions"), ingredients)
        }
    }
}
class MealRepository(private val dao: MealDao) {
    val favorites = dao.observe()
    private val cache = mutableMapOf<String, Meal>()
    private suspend fun request(path: String): List<Meal> = withContext(Dispatchers.IO) {
        val connection = URL("https://www.themealdb.com/api/json/v1/1/$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        try {
            check(connection.responseCode in 200..299) { "Serverfehler (${connection.responseCode})" }
            MealParser.parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }
    suspend fun search(query: String): List<Meal> = request("search.php?s=" + URLEncoder.encode(query, "UTF-8"))
        .also { meals -> meals.forEach { cache[it.id] = it } }
    suspend fun detail(id: String): Meal = dao.find(id) ?: cache[id]
        ?: request("lookup.php?i=" + URLEncoder.encode(id, "UTF-8")).firstOrNull()?.also { cache[id] = it }
        ?: error("Rezept nicht gefunden")
    suspend fun toggle(meal: Meal) {
        if (dao.find(meal.id) == null) dao.save(meal) else dao.delete(meal.id)
    }
}
