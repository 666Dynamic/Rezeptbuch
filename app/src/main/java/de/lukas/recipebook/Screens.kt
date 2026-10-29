package de.lukas.recipebook

import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import androidx.core.os.bundleOf
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

fun View.dp(value: Int) = (value * resources.displayMetrics.density).toInt()
fun Context.column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding((16 * resources.displayMetrics.density).toInt()) }
fun LinearLayout.text(value: String, size: Float = 16f): TextView = TextView(context).also {
    it.text = value; it.textSize = size; it.setPadding(0, 12, 0, 12); addView(it)
}
fun LinearLayout.button(label: String, action: () -> Unit): MaterialButton = MaterialButton(context).also {
    it.text = label; it.setOnClickListener { action() }; addView(it)
}
fun Fragment.openMeal(meal: Meal) = findNavController().navigate(R.id.detailFragment, bundleOf("mealId" to meal.id))

class MealAdapter(private val open: (Meal) -> Unit) : RecyclerView.Adapter<MealAdapter.Holder>() {
    private var meals = emptyList<Meal>()
    fun submit(value: List<Meal>) { meals = value; notifyDataSetChanged() }
    class Holder(val row: LinearLayout, val photo: ImageView, val title: TextView, val subtitle: TextView) : RecyclerView.ViewHolder(row)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val row = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8)); minimumHeight = dp(108)
            layoutParams = RecyclerView.LayoutParams(-1, -2)
            isFocusable = true; isClickable = true
        }
        val photo = ImageView(parent.context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        row.addView(photo, LinearLayout.LayoutParams(row.dp(88), row.dp(88)))
        val words = parent.context.column()
        row.addView(words, LinearLayout.LayoutParams(0, -2, 1f))
        return Holder(row, photo, words.text("", 18f), words.text("", 14f))
    }
    override fun getItemCount() = meals.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val meal = meals[position]
        holder.title.text = meal.name
        holder.subtitle.text = listOf(meal.category, meal.area).filter { it.isNotBlank() }.joinToString(" · ")
        holder.photo.contentDescription = meal.name
        holder.photo.load(meal.image) { crossfade(true); placeholder(android.R.drawable.ic_menu_gallery); error(android.R.drawable.ic_menu_report_image) }
        holder.row.setOnClickListener { open(meal) }
    }
}

abstract class MealListFragment : Fragment() {
    protected val model: RecipeViewModel by activityViewModels()
    protected lateinit var container: LinearLayout
    protected lateinit var status: TextView
    protected lateinit var adapter: MealAdapter
    private lateinit var list: RecyclerView
    protected fun createList(): View {
        container = requireContext().column()
        status = container.text("")
        adapter = MealAdapter { openMeal(it) }
        list = RecyclerView(requireContext()).apply { layoutManager = LinearLayoutManager(context); adapter = this@MealListFragment.adapter }
        container.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        return container
    }
    protected fun show(meals: List<Meal>, empty: String) {
        adapter.submit(meals)
        status.text = if (meals.isEmpty()) empty else "${meals.size} Rezepte"
    }
    protected suspend fun load(query: String) {
        status.text = "Rezepte werden geladen …"
        try { show(model.repository.search(query), "Keine Rezepte gefunden. Versuche einen anderen englischen Suchbegriff.") }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { adapter.submit(emptyList()); status.text = "Laden fehlgeschlagen. Prüfe deine Internetverbindung und versuche es erneut." }
    }
}

class HomeFragment : MealListFragment() {
    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View = createList().also {
        val header = requireContext().column().apply {
            text("Was kochen wir heute?", 26f)
            text("Entdecke neue Rezepte und speichere deine Favoriten. Die Rezepte von TheMealDB sind auf Englisch.")
            button("Rezepte neu laden") { viewLifecycleOwner.lifecycleScope.launch { load("") } }
        }
        container.addView(header, 0)
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch { load("") }
    }
}

class SearchFragment : MealListFragment() {
    private var request: Job? = null
    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View = createList().also {
        val header = requireContext().column()
        header.text("Finde dein nächstes Lieblingsgericht", 24f)
        val input = EditText(requireContext()).apply {
            hint = "Zum Beispiel: chicken, pasta, soup"
            contentDescription = "Rezeptname auf Englisch"
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setText(model.searchQuery)
        }
        header.addView(input)
        fun search() {
            model.searchQuery = input.text.toString().trim()
            request?.cancel()
            request = viewLifecycleOwner.lifecycleScope.launch { load(model.searchQuery) }
            (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        }
        header.button("Suchen") { search() }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { search(); true } else false
        }
        container.addView(header, 0)
        status.text = "Suche nach einem Gericht oder einer Zutat im Rezeptnamen."
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        if (model.searchQuery.isNotEmpty()) request = viewLifecycleOwner.lifecycleScope.launch { load(model.searchQuery) }
    }
}

class FavoritesFragment : MealListFragment() {
    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View = createList().also {
        container.addView(TextView(requireContext()).apply { text = "Deine gespeicherten Rezepte – auch offline verfügbar"; textSize = 22f }, 0)
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.repository.favorites.collect { show(it, "Noch keine Favoriten. Öffne ein Rezept und tippe auf Speichern.") }
            }
        }
    }
}

class DetailFragment : Fragment() {
    private val model: RecipeViewModel by activityViewModels()
    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View = ScrollView(requireContext()).apply { addView(context.column()) }
    override fun onViewCreated(view: View, state: Bundle?) {
        val body = (view as ScrollView).getChildAt(0) as LinearLayout
        val id = requireArguments().getString("mealId") ?: return
        fun render() {
            body.removeAllViews()
            val status = body.text("Rezept wird geladen …")
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val meal = model.repository.detail(id)
                    body.removeAllViews()
                    body.text(meal.name, 28f)
                    body.text("${meal.category} · ${meal.area}")
                    val photo = ImageView(requireContext()).apply { scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = meal.name }
                    body.addView(photo, LinearLayout.LayoutParams(-1, photo.dp(220)))
                    photo.load(meal.image) { placeholder(android.R.drawable.ic_menu_gallery); error(android.R.drawable.ic_menu_report_image) }
                    val savedStatus = body.text("")
                    val save = body.button("Speichern") {}
                    save.setOnClickListener {
                        viewLifecycleOwner.lifecycleScope.launch {
                            save.isEnabled = false
                            try { model.repository.toggle(meal); savedStatus.text = "" }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { savedStatus.text = "Speichern fehlgeschlagen. Bitte erneut versuchen." }
                            finally { save.isEnabled = true }
                        }
                    }
                    viewLifecycleOwner.lifecycleScope.launch {
                        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            model.repository.favorites.collect { favorites ->
                                save.text = if (favorites.any { it.id == id }) "Aus Favoriten entfernen" else "In Favoriten speichern"
                            }
                        }
                    }
                    body.text("Zutaten", 22f)
                    body.text(meal.ingredients.ifBlank { "Keine Zutaten angegeben." })
                    body.text("Zubereitung", 22f)
                    body.text(meal.instructions.ifBlank { "Keine Anleitung verfügbar." })
                    body.text("Rezept und Foto: TheMealDB", 13f)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    status.text = "Rezept konnte nicht geladen werden. Gespeicherte Favoriten sind auch offline verfügbar."
                    body.button("Erneut versuchen") { render() }
                }
            }
        }
        render()
    }
}

class AboutFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View = ScrollView(requireContext()).apply {
        addView(context.column().apply {
            text("Rezeptbuch", 30f)
            text("Neue Gerichte entdecken. Lieblingsrezepte behalten.", 20f)
            text("Suche nach englischen Rezeptnamen. Öffne einen Treffer für Zutaten und Zubereitung. Gespeicherte Rezepte bleiben auf diesem Gerät und können ohne Internet gelesen werden.")
            text("Datenquelle", 22f)
            text("Rezepte und Bilder werden von TheMealDB bereitgestellt. Die kostenlose Test-API wird für dieses Lernprojekt verwendet. Für eine öffentliche Veröffentlichung ist die passende API-Lizenz zu prüfen.")
            text("https://www.themealdb.com", 16f).apply { autoLinkMask = android.text.util.Linkify.WEB_URLS; movementMethod = android.text.method.LinkMovementMethod.getInstance() }
            text("Datenschutz", 22f)
            text("Kein Benutzerkonto, keine Werbung und kein Tracking. Favoriten werden lokal mit Room gespeichert. Bei der Suche wird der Suchbegriff an TheMealDB übertragen; Bilder werden ebenfalls von dort geladen. Beim Deinstallieren werden die lokalen Favoriten gelöscht.")
            text("Version 1.0 · Android-Abschlussprojekt", 13f)
        })
    }
}
