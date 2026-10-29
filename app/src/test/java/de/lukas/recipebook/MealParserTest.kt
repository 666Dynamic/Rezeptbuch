package de.lukas.recipebook

import org.junit.Assert.*
import org.junit.Test

class MealParserTest {
    @Test fun missingResultsProduceEmptyList() {
        assertTrue(MealParser.parse("""{"meals":null}""").isEmpty())
    }
    @Test fun ingredientsExcludeBlankAndNullFields() {
        val meal = MealParser.parse("""{"meals":[{"idMeal":"42","strMeal":"Soup","strIngredient1":"Onion","strMeasure1":"2","strIngredient2":null,"strIngredient3":"  ","strInstructions":"Cook"}]}""").single()
        assertEquals("42", meal.id)
        assertEquals("2 Onion", meal.ingredients)
        assertEquals("Cook", meal.instructions)
        assertEquals("", meal.image)
    }
}
