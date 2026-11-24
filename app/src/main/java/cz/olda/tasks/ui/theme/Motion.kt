package cz.olda.tasks.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Material 3 Expressive motion, copied rather than read.
 *
 * material3 1.4.0 keeps the whole scheme internal — `MotionScheme`, `MotionScheme.expressive()`,
 * `MaterialTheme.motionScheme` and `MaterialExpressiveTheme` are all `internal` — so app code
 * cannot ask the library for these specs. The values below are its `ExpressiveMotionTokens`
 * (`SpringDefaultSpatialDamping` and friends, generated from Material token set v0_14_0),
 * named here the way the scheme names them so a later reader can diff the two.
 *
 * `MotionTest` pins every number. If a future Compose version makes the scheme public, delete
 * this object and read it from the library instead.
 *
 * **Spatial** specs move or resize things and are allowed to overshoot. **Effects** specs carry
 * colour and alpha, where overshoot would be a flicker, so they are critically damped.
 */
object AppMotion {

    fun <T> spatial(): SpringSpec<T> = cast(DefaultSpatial)

    fun <T> fastSpatial(): SpringSpec<T> = cast(FastSpatial)

    fun <T> slowSpatial(): SpringSpec<T> = cast(SlowSpatial)

    fun <T> effects(): SpringSpec<T> = cast(DefaultEffects)

    fun <T> fastEffects(): SpringSpec<T> = cast(FastEffects)

    fun <T> slowEffects(): SpringSpec<T> = cast(SlowEffects)

    private val DefaultSpatial = spring<Any>(dampingRatio = 0.8f, stiffness = 380f)
    private val FastSpatial = spring<Any>(dampingRatio = 0.6f, stiffness = 800f)
    private val SlowSpatial = spring<Any>(dampingRatio = 0.8f, stiffness = 200f)
    private val DefaultEffects = spring<Any>(dampingRatio = 1.0f, stiffness = 1600f)
    private val FastEffects = spring<Any>(dampingRatio = 1.0f, stiffness = 3800f)
    private val SlowEffects = spring<Any>(dampingRatio = 1.0f, stiffness = 800f)

    /**
     * One instance per spec rather than one per call site, as the library itself does. The cast
     * is safe because a `SpringSpec` holds nothing of type `T` once `visibilityThreshold` is
     * null, which it is for all six: damping and stiffness are plain floats, and the type only
     * reappears when the spec is vectorised against a converter at animation time.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> cast(spec: SpringSpec<Any>): SpringSpec<T> = spec as SpringSpec<T>
}

/**
 * False when the phone's animator duration scale is 0, which is what Accessibility →
 * Remove animations sets. Separate from the composable that reads the setting so it can be
 * tested without a device.
 */
fun animationsEnabled(animatorScale: Float): Boolean = animatorScale != 0f

/**
 * Whether this app may animate. Everything that moves checks it and jumps to its final state
 * when it is false; nothing else about the app changes.
 */
val LocalAnimationsEnabled: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { true }
