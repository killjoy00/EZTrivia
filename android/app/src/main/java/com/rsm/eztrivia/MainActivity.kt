package com.rsm.eztrivia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rsm.eztrivia.data.QuestionCatalog
import com.rsm.eztrivia.model.TriviaCategory
import com.rsm.eztrivia.model.TriviaQuestion

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EZTriviaHome()
                }
            }
        }
    }
}

@Composable
private fun EZTriviaHome() {
    val context = LocalContext.current
    val catalogResult by produceState<Result<List<TriviaQuestion>>?>(initialValue = null) {
        value = runCatching { QuestionCatalog.load(context) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "EZ Trivia",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = when {
                catalogResult == null -> "Loading the shared question catalog..."
                catalogResult?.isSuccess == true -> "${catalogResult!!.getOrThrow().size} questions ready"
                else -> "Question catalog failed to load"
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Android foundation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "The native Compose client is now wired to the same verified content source as iOS. Gameplay and platform services layer on top of this contract next.",
            style = MaterialTheme.typography.bodyMedium,
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(TriviaCategory.entries) { category ->
                CategoryCard(category)
            }
        }
    }
}

@Composable
private fun CategoryCard(category: TriviaCategory) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Content linked",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
