package de.lukas.recipebook

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class RecipeViewModel(app: Application) : AndroidViewModel(app) {
    val repository = MealRepository(MealDatabase.get(app).meals())
    var searchQuery = ""
}
