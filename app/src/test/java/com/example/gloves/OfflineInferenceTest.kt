package com.example.gloves

import com.silentbridge.data.repository.FallbackQwenRepository
import com.silentbridge.domain.inference.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OfflineInferenceTest {

    // ── IntentInterpreter ─────────────────────────────────────────────────────

    @Test
    fun testInterpreter_greeting() {
        val interpreter = IntentInterpreter()
        val result = interpreter.interpret(listOf("HELLO"))
        assertEquals(GestureIntent.GREETING, result.intent)
        assertEquals("HELLO", result.greeting)
        assertNull(result.subject)
        assertTrue(result.objects.isEmpty())
    }

    @Test
    fun testInterpreter_request() {
        val interpreter = IntentInterpreter()
        val result = interpreter.interpret(listOf("I", "NEED", "FOOD", "WATER"))
        assertEquals(GestureIntent.REQUEST, result.intent)
        assertEquals("I", result.subject)
        assertTrue(result.objects.contains("FOOD"))
        assertTrue(result.objects.contains("WATER"))
    }

    @Test
    fun testInterpreter_emergency() {
        val interpreter = IntentInterpreter()
        val result = interpreter.interpret(listOf("HELP", "MEDICINE"))
        assertEquals(GestureIntent.EMERGENCY, result.intent)
        assertTrue(result.isEmergency)
    }

    @Test
    fun testInterpreter_confirmation() {
        val interpreter = IntentInterpreter()
        assertEquals(GestureIntent.CONFIRMATION, interpreter.interpret(listOf("YES")).intent)
    }

    @Test
    fun testInterpreter_negation() {
        val interpreter = IntentInterpreter()
        assertEquals(GestureIntent.NEGATION, interpreter.interpret(listOf("NO")).intent)
    }

    @Test
    fun testInterpreter_closing() {
        val interpreter = IntentInterpreter()
        val result = interpreter.interpret(listOf("THANK_YOU"))
        assertEquals(GestureIntent.CLOSING, result.intent)
        assertEquals("THANK_YOU", result.closing)
    }

    // ── PromptBuilder ─────────────────────────────────────────────────────────

    @Test
    fun testPromptBuilder_containsSystemMarker() {
        val builder = PromptBuilder()
        val structure = ParsedStructure(
            intent = GestureIntent.REQUEST,
            subject = "I",
            objects = listOf("WATER", "FOOD")
        )
        val prompt = builder.buildPrompt(structure)
        assertTrue(prompt.contains("<|im_start|>system"))
        assertTrue(prompt.contains("<|im_start|>assistant"))
        assertTrue(prompt.contains("Request"))
    }

    @Test
    fun testPromptBuilder_greetingNone() {
        val builder = PromptBuilder()
        val structure = ParsedStructure(intent = GestureIntent.REQUEST, subject = "I", objects = listOf("WATER"))
        val prompt = builder.buildPrompt(structure)
        assertTrue(prompt.contains("None"))  // greeting is None
    }

    // ── SemanticValidator ─────────────────────────────────────────────────────

    @Test
    fun testValidator_allowsHelperWords() {
        val validator = SemanticValidator()
        val tokens = listOf("I", "NEED", "FOOD", "WATER")
        val structure = ParsedStructure(GestureIntent.REQUEST, objects = listOf("FOOD", "WATER"))
        assertTrue(validator.isValid("I need food and water.", structure, tokens))
    }

    @Test
    fun testValidator_allowsArticles() {
        val validator = SemanticValidator()
        val tokens = listOf("I", "NEED", "FOOD")
        val structure = ParsedStructure(GestureIntent.REQUEST, objects = listOf("FOOD"))
        assertTrue(validator.isValid("I need the food.", structure, tokens))
    }

    @Test
    fun testValidator_rejectsUnrelatedSentence() {
        val validator = SemanticValidator()
        val tokens = listOf("FOOD", "WATER")
        val structure = ParsedStructure(GestureIntent.REQUEST, objects = listOf("FOOD", "WATER"))
        assertFalse(validator.isValid("Quantum physics theorem.", structure, tokens))
    }

    @Test
    fun testValidator_rejectsMissingObjects() {
        val validator = SemanticValidator()
        val tokens = listOf("I", "WANT", "WATER")
        val structure = ParsedStructure(GestureIntent.REQUEST, objects = listOf("WATER"))
        // Sentence mentions "I want food" but misses the core object "WATER"
        assertFalse(validator.isValid("I want food.", structure, tokens))
    }

    @Test
    fun testValidator_underscoreTokensSplit() {
        val validator = SemanticValidator()
        val tokens = listOf("THANK_YOU")
        val structure = ParsedStructure(GestureIntent.CLOSING)
        assertTrue(validator.isValid("Thank you.", structure, tokens))
    }

    // ── FallbackQwenRepository ────────────────────────────────────────────────

    @Test
    fun testFallback_extractsObjectsFromPrompt() = runBlocking {
        val repo = FallbackQwenRepository()
        // Use the new structured prompt format from PromptBuilder
        val fakePrompt = """<|im_start|>system
You are an ISL translator.<|im_end|>
<|im_start|>user
Convert the following ISL gesture data into one English sentence.

Intent: Request
Subject: I
Verbs (hints): None
Objects: WATER, FOOD

Output only the final English sentence:<|im_end|>
<|im_start|>assistant
""".trimIndent()

        val result = repo.generateSentence(fakePrompt)
        assertTrue(result.isSuccess)
        val sentence = result.getOrNull()!!
        // Should produce something like "I need water and food."
        assertTrue("Expected water in: $sentence", sentence.lowercase().contains("water"))
        assertTrue("Expected food in: $sentence", sentence.lowercase().contains("food"))
    }

    // ── LanguageEngine fast-path ──────────────────────────────────────────────

    @Test
    fun testLanguageEngine_fastPathYes() = runBlocking {
        val engine = LanguageEngine(
            IntentInterpreter(),
            PromptBuilder(),
            SemanticValidator(),
            FallbackQwenRepository()
        )
        val result = engine.reconstructSentence(listOf("YES"))
        assertTrue(result.isSuccess)
        assertEquals("Yes.", result.getOrNull())
    }

    @Test
    fun testLanguageEngine_fastPathNo() = runBlocking {
        val engine = LanguageEngine(
            IntentInterpreter(),
            PromptBuilder(),
            SemanticValidator(),
            FallbackQwenRepository()
        )
        val result = engine.reconstructSentence(listOf("NO"))
        assertTrue(result.isSuccess)
        assertEquals("No.", result.getOrNull())
    }

    @Test
    fun testLanguageEngine_fastPathHello() = runBlocking {
        val engine = LanguageEngine(
            IntentInterpreter(),
            PromptBuilder(),
            SemanticValidator(),
            FallbackQwenRepository()
        )
        val result = engine.reconstructSentence(listOf("HELLO"))
        assertTrue(result.isSuccess)
        assertEquals("Hello.", result.getOrNull())
    }

    @Test
    fun testLanguageEngine_fastPathThankYou() = runBlocking {
        val engine = LanguageEngine(
            IntentInterpreter(),
            PromptBuilder(),
            SemanticValidator(),
            FallbackQwenRepository()
        )
        val result = engine.reconstructSentence(listOf("THANK_YOU"))
        assertTrue(result.isSuccess)
        assertEquals("Thank you.", result.getOrNull())
    }

    @Test
    fun testLanguageEngine_withFallbackRepo() = runBlocking {
        val engine = LanguageEngine(
            IntentInterpreter(),
            PromptBuilder(),
            SemanticValidator(),
            FallbackQwenRepository()
        )
        val result = engine.reconstructSentence(listOf("I", "NEED", "WATER"))
        assertTrue(result.isSuccess)
        val sentence = result.getOrNull()!!
        // FallbackQwenRepository produces "I need water." via rule-based generation
        assertTrue("Expected water in: $sentence", sentence.lowercase().contains("water"))
    }
}
