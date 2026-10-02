package io.github.patissiermongs.foldkey.input

import io.github.patissiermongs.foldkey.engine.Gesture
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.Modifier
import io.github.patissiermongs.foldkey.layout.Box
import io.github.patissiermongs.foldkey.layout.Key
import io.github.patissiermongs.foldkey.layout.KeyDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchTrackerTest {
    private fun key(def: KeyDef, col: Int): Key {
        val box = Box(col * 100f, 0f, col * 100f + 100f, 100f)
        return Key(def, box, box, 0, 0)
    }

    private val a = key(KeyDef(KeyAction.Char('a', 'A')), 0)
    private val b = key(KeyDef(KeyAction.Char('b', 'B')), 1)
    private val shift = key(KeyDef(KeyAction.Mod(Modifier.SHIFT)), 2)
    private val bs = key(KeyDef(KeyAction.Backspace, repeat = true), 3)
    private val space = key(KeyDef(KeyAction.Space, width = 1f), 4)
    private val arrow = key(
        KeyDef(
            KeyAction.Code(android.view.KeyEvent.KEYCODE_DPAD_LEFT),
            up = KeyAction.Code(android.view.KeyEvent.KEYCODE_MOVE_HOME),
            repeat = true,
        ),
        5,
    )
    private val keys = listOf(a, b, shift, bs, space, arrow)

    private class Sink(val keys: List<Key>) : TouchSink {
        val events = ArrayList<String>()
        val fired = ArrayList<Pair<Key, Gesture>>()
        var cursorSteps = 0

        override fun hit(x: Float, y: Float, t: Long): Key? = keys.firstOrNull { it.touch.contains(x, y) }
        override fun keyDown(pointer: Int, key: Key, t: Long) { events.add("down:${label(key)}") }
        override fun modifierDown(key: Key, t: Long) { events.add("mod+:${label(key)}") }
        override fun modifierUp(key: Key, t: Long) { events.add("mod-:${label(key)}") }
        override fun modifierCancel(key: Key, t: Long) { events.add("modx:${label(key)}") }
        override fun fire(key: Key, gesture: Gesture, t: Long) {
            events.add("fire:${label(key)}:$gesture@$t")
            fired.add(key to gesture)
        }
        override fun variant(pointer: Int, key: Key, gesture: Gesture) {
            if (gesture == Gesture.LONG) events.add("hold:${label(key)}")
        }
        override fun released(pointer: Int) {}
        override fun cursor(steps: Int, select: Boolean) {
            cursorSteps += steps
            events.add((if (select) "select:" else "cursor:") + steps)
        }
        override fun cursorRows(steps: Int, select: Boolean) { events.add((if (select) "selectRows:" else "rows:") + steps) }
        override fun cursorEnd() { events.add("cursorEnd") }
        override fun sample(key: Key, x: Float, y: Float) { events.add("sample:${label(key)}") }

        private fun label(k: Key): String = when (val a = k.def.action) {
            is KeyAction.Char -> a.base.toString()
            is KeyAction.Mod -> a.modifier.name
            else -> a.toString()
        }
    }

    private fun tracker(sink: Sink, longPress: Long = 0L, repeats: Boolean = false, hold: Long = 400L) = TouchTracker(
        sink,
        TouchParams(
            swipeThresholdPx = 30f,
            cursorStartPx = 20f,
            cursorStepPx = 10f,
            cursorRowStepPx = 16f,
            longPressMs = longPress,
            longPressRepeats = repeats,
            selectHoldMs = hold,
        ),
    )

    @Test
    fun tapFiresOnUpAndRecordsSample() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 50f, 50f, 0)
        t.move(0, 55f, 58f, 30)
        t.up(0, 55f, 58f, 80)
        assertEquals(listOf("down:a", "fire:a:TAP@80", "sample:a"), s.events)
    }

    @Test
    fun verticalSwipesAreClassified() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 50f, 60f, 0); t.move(0, 52f, 20f, 40); t.up(0, 52f, 20f, 60)
        t.down(1, 150f, 20f, 100); t.move(1, 150f, 70f, 140); t.up(1, 150f, 70f, 160)
        t.down(2, 50f, 50f, 200); t.move(2, 90f, 80f, 240); t.up(2, 90f, 80f, 260)
        assertEquals(listOf(Gesture.UP, Gesture.DOWN, Gesture.TAP), s.fired.map { it.second })
    }

    private fun firedChars(s: Sink) = s.fired.map { (it.first.def.action as KeyAction.Char).base.toString() }

    @Test
    fun rolloverKeepsTouchDownOrderWhenSecondFingerLiftsFirst() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 50f, 50f, 0)
        t.down(1, 150f, 50f, 40)
        assertEquals(0, s.fired.size)
        t.up(1, 150f, 50f, 90)
        t.up(0, 50f, 50f, 120)
        assertEquals(listOf("a", "b"), firedChars(s))
        assertEquals("fire:a:TAP@90", s.events.first { it.startsWith("fire:a") })
    }

    @Test
    fun rolloverWhenFirstFingerLiftsFirst() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 50f, 50f, 0)
        t.down(1, 150f, 50f, 40)
        t.up(0, 50f, 50f, 70)
        t.up(1, 150f, 50f, 110)
        assertEquals(listOf("a", "b"), firedChars(s))
        assertEquals(listOf("fire:a:TAP@70", "fire:b:TAP@110"), s.events.filter { it.startsWith("fire") })
    }

    @Test
    fun threeFingerRolloverCommitsOlderPointersFirst() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 50f, 50f, 0)
        t.down(1, 150f, 50f, 20)
        t.down(2, 50f, 50f, 40)
        t.up(2, 50f, 50f, 60)
        t.up(0, 50f, 50f, 80)
        t.up(1, 150f, 50f, 90)
        assertEquals(listOf("a", "b", "a"), firedChars(s))
    }

    @Test
    fun modifierDownCommitsPendingKeyFirst() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 50f, 50f, 0)
        t.down(1, 250f, 50f, 30)
        t.up(0, 50f, 50f, 60)
        t.up(1, 250f, 50f, 90)
        assertEquals(listOf("down:a", "fire:a:TAP@30", "sample:a", "down:SHIFT", "mod+:SHIFT", "mod-:SHIFT"), s.events)
    }

    @Test
    fun canceledPointerDoesNotType() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 50f, 50f, 0)
        t.cancel(0, 50)
        t.up(0, 50f, 50f, 60)
        assertEquals(0, s.fired.size)
    }

    @Test
    fun repeatKeyCommitsOlderPendingKeyBeforeFiring() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 50f, 50f, 0)
        t.down(1, 350f, 50f, 30)
        assertEquals(listOf(Gesture.TAP, Gesture.TAP), s.fired.map { it.second })
        assertEquals(a, s.fired[0].first)
        assertEquals(bs, s.fired[1].first)
    }

    @Test
    fun modifierRolledIntoTheNextKeyIsReleasedFirst() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 250f, 50f, 0)
        t.down(1, 50f, 50f, 60)
        t.up(0, 250f, 50f, 100)
        t.up(1, 50f, 50f, 140)
        assertEquals(listOf("down:SHIFT", "mod+:SHIFT", "down:a", "mod-:SHIFT", "fire:a:TAP@140", "sample:a"), s.events)
    }

    @Test
    fun modifierReleasedFirstAfterADeliberatePressStillChords() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 250f, 50f, 0)
        t.down(1, 50f, 50f, 200)
        t.up(0, 250f, 50f, 300)
        t.up(1, 50f, 50f, 340)
        assertEquals(listOf("down:SHIFT", "mod+:SHIFT", "down:a", "fire:a:TAP@300", "sample:a", "mod-:SHIFT"), s.events)
    }

    @Test
    fun modifierAlreadyUsedChordsTheKeyStillDown() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 250f, 50f, 0)
        t.down(1, 50f, 50f, 20)
        t.up(1, 50f, 50f, 60)
        t.down(1, 150f, 50f, 80)
        t.up(0, 250f, 50f, 100)
        t.up(1, 150f, 50f, 130)
        assertEquals(
            listOf("down:SHIFT", "mod+:SHIFT", "down:a", "fire:a:TAP@60", "sample:a", "down:b", "fire:b:TAP@100", "sample:b", "mod-:SHIFT"),
            s.events,
        )
    }

    @Test
    fun canceledModifierIsNotReleased() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 250f, 50f, 0)
        t.cancel(0, 30)
        assertEquals(listOf("down:SHIFT", "mod+:SHIFT", "modx:SHIFT"), s.events)
    }

    @Test
    fun newKeyStopsTheRepeatOfAnotherKey() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 350f, 50f, 0)
        t.tick(400)
        t.tick(450)
        t.down(1, 50f, 50f, 470)
        assertNull(t.nextDeadline())
        t.up(1, 50f, 50f, 520)
        t.up(0, 350f, 50f, 600)
        assertEquals(listOf(bs to Gesture.TAP, bs to Gesture.REPEAT, bs to Gesture.REPEAT, a to Gesture.TAP), s.fired)
    }

    @Test
    fun spaceHoldIsDisarmedByAKeyPressedDuringIt() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 450f, 50f, 0)
        t.down(1, 50f, 50f, 150)
        t.tick(400)
        t.up(1, 50f, 50f, 420)
        t.up(0, 450f, 50f, 430)
        assertEquals(listOf(space, a), s.fired.map { it.first })
        assertEquals(listOf(Gesture.TAP, Gesture.TAP), s.fired.map { it.second })
    }

    @Test
    fun flushPendingFiresHeldKeysInPressOrder() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 150f, 50f, 0)
        t.down(1, 50f, 50f, 20)
        t.flushPending(40)
        t.up(0, 150f, 50f, 60)
        t.up(1, 50f, 50f, 80)
        assertEquals(listOf(b, a), s.fired.map { it.first })
    }

    @Test
    fun modifierHeldDuringTapIsNotCommittedEarly() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 250f, 50f, 0)
        t.down(1, 50f, 50f, 50)
        t.up(1, 50f, 50f, 100)
        t.up(0, 250f, 50f, 150)
        assertEquals(listOf("down:SHIFT", "mod+:SHIFT", "down:a", "fire:a:TAP@100", "sample:a", "mod-:SHIFT"), s.events)
    }

    @Test
    fun backspaceFiresOnDownAndRepeats() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 350f, 50f, 0)
        assertEquals(400L, t.nextDeadline())
        t.tick(400)
        t.tick(450)
        t.tick(500)
        t.up(0, 350f, 50f, 520)
        assertEquals(listOf(Gesture.TAP, Gesture.REPEAT, Gesture.REPEAT, Gesture.REPEAT), s.fired.map { it.second })
        assertNull(t.nextDeadline())
    }

    @Test
    fun longPressFiresOnceAndSuppressesTap() {
        val s = Sink(keys)
        val t = tracker(s, longPress = 350L)
        t.down(0, 50f, 50f, 0)
        t.tick(350)
        t.up(0, 50f, 50f, 600)
        assertEquals(listOf(Gesture.LONG), s.fired.map { it.second })
    }

    @Test
    fun swipeCancelsLongPress() {
        val s = Sink(keys)
        val t = tracker(s, longPress = 350L)
        t.down(0, 50f, 60f, 0)
        t.move(0, 50f, 10f, 100)
        assertNull(t.nextDeadline())
        t.up(0, 50f, 10f, 500)
        assertEquals(listOf(Gesture.UP), s.fired.map { it.second })
    }

    @Test
    fun spaceDragMovesCursorWithoutTyping() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 450f, 50f, 0)
        t.move(0, 475f, 50f, 20)
        t.move(0, 505f, 50f, 40)
        t.move(0, 480f, 50f, 60)
        t.up(0, 480f, 50f, 80)
        assertEquals(0, s.fired.size)
        assertEquals(1, s.cursorSteps)
        assertEquals("cursorEnd", s.events.last())
    }

    @Test
    fun spaceDragUpOrDownLocksToRowsAndIgnoresSideways() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 450f, 50f, 0)
        t.move(0, 452f, 25f, 20)
        t.move(0, 470f, 10f, 40)
        t.move(0, 490f, -20f, 60)
        t.move(0, 490f, 30f, 80)
        t.up(0, 490f, 30f, 100)
        assertEquals(listOf("down:Space", "rows:-1", "rows:-2", "rows:3", "cursorEnd"), s.events)
        assertEquals(0, s.fired.size)
        assertEquals(0, s.cursorSteps)
    }

    @Test
    fun diagonalSpaceDragPicksHorizontalWhenNotClearlyVertical() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 450f, 50f, 0)
        t.move(0, 471f, 30f, 20)
        t.move(0, 481f, 0f, 40)
        t.up(0, 481f, 0f, 60)
        assertEquals(listOf("down:Space", "cursor:1", "cursorEnd"), s.events)
    }

    @Test
    fun longPressRepeatsTheKeyWhenEnabled() {
        val s = Sink(keys)
        val t = tracker(s, longPress = 400L, repeats = true)
        t.down(0, 50f, 50f, 0)
        t.tick(400)
        t.tick(450)
        t.tick(500)
        t.up(0, 50f, 50f, 520)
        assertEquals(listOf(Gesture.TAP, Gesture.REPEAT, Gesture.REPEAT), s.fired.map { it.second })
        assertEquals(listOf("down:a", "fire:a:TAP@400", "fire:a:REPEAT@450", "fire:a:REPEAT@500"), s.events)
    }

    @Test
    fun swipeBeforeDelayStillGivesVariantWhenRepeatIsEnabled() {
        val s = Sink(keys)
        val t = tracker(s, longPress = 400L, repeats = true)
        t.down(0, 50f, 60f, 0)
        t.move(0, 50f, 10f, 100)
        t.tick(400)
        t.up(0, 50f, 10f, 450)
        assertEquals(listOf(Gesture.UP), s.fired.map { it.second })
    }

    @Test
    fun spaceHeldStillThenDraggedSelectsInsteadOfTyping() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 450f, 50f, 0)
        assertEquals(400L, t.nextDeadline())
        t.move(0, 455f, 53f, 200)
        t.tick(400)
        assertNull(t.nextDeadline())
        t.move(0, 470f, 53f, 450)
        t.move(0, 486f, 55f, 500)
        t.move(0, 464f, 52f, 550)
        t.up(0, 464f, 52f, 600)
        assertEquals(listOf("down:Space", "hold:Space", "select:1", "select:-2", "cursorEnd"), s.events)
        assertEquals(0, s.fired.size)
    }

    @Test
    fun spaceHeldThenDraggedDownSelectsRows() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 450f, 50f, 0)
        t.tick(400)
        t.move(0, 452f, 90f, 450)
        t.move(0, 460f, 69f, 500)
        t.up(0, 460f, 69f, 550)
        assertEquals(listOf("down:Space", "hold:Space", "selectRows:1", "selectRows:-1", "cursorEnd"), s.events)
    }

    @Test
    fun spaceHeldAndReleasedWithoutMovingTypesNothing() {
        val s = Sink(keys)
        val t = tracker(s, longPress = 300L, repeats = true, hold = 500L)
        t.down(0, 450f, 50f, 0)
        assertEquals(500L, t.nextDeadline())
        t.tick(300)
        assertEquals(0, s.events.count { it.startsWith("hold") })
        t.tick(500)
        t.tick(550)
        t.up(0, 450f, 50f, 900)
        assertEquals(listOf("down:Space", "hold:Space"), s.events)
    }

    @Test
    fun spaceDragBeforeTheHoldMovesTheCursorAndNeverStartsSelecting() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 450f, 50f, 0)
        t.move(0, 475f, 50f, 100)
        assertNull(t.nextDeadline())
        t.tick(400)
        t.move(0, 485f, 50f, 500)
        t.up(0, 485f, 50f, 600)
        assertEquals(listOf("down:Space", "cursor:1", "cursorEnd"), s.events)
    }

    @Test
    fun anotherKeyPressedDuringTheHoldTypesSpaceFirst() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 450f, 50f, 0)
        t.down(1, 50f, 50f, 100)
        t.up(1, 50f, 50f, 150)
        t.tick(400)
        t.up(0, 450f, 50f, 500)
        assertEquals(listOf(Gesture.TAP, Gesture.TAP), s.fired.map { it.second })
        assertEquals(listOf(space, a), s.fired.map { it.first })
    }

    @Test
    fun spaceTapTypesSpace() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 450f, 50f, 0)
        t.move(0, 455f, 52f, 20)
        t.up(0, 455f, 52f, 60)
        assertEquals(listOf(Gesture.TAP), s.fired.map { it.second })
    }

    @Test
    fun arrowWithSwipeVariantFiresOnReleaseOrRepeatsWhenHeld() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 550f, 50f, 0)
        assertEquals(0, s.fired.size)
        t.up(0, 550f, 50f, 90)
        t.down(1, 550f, 60f, 200)
        t.move(1, 550f, 10f, 240)
        t.up(1, 550f, 10f, 260)
        t.down(2, 550f, 50f, 400)
        t.tick(800)
        t.tick(850)
        t.up(2, 550f, 50f, 870)
        assertEquals(listOf(Gesture.TAP, Gesture.UP, Gesture.TAP, Gesture.REPEAT), s.fired.map { it.second })
    }

    @Test
    fun touchOutsideKeysIsIgnored() {
        val s = Sink(keys)
        val t = tracker(s)
        t.down(0, 50f, 500f, 0)
        t.up(0, 50f, 500f, 50)
        assertEquals(0, s.events.size)
    }
}
