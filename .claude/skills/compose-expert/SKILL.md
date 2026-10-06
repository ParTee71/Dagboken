---
name: compose-expert
description: >
  Compose and Compose Multiplatform expert for UI development across Android, Desktop,
  iOS, and Web. Use whenever the user mentions Compose APIs (@Composable, remember,
  LaunchedEffect, NavHost, MaterialTheme, LazyColumn, Modifier, recomposition),
  Compose Multiplatform (commonMain, expect/actual, Res.*, ComposeUIViewController,
  UIKitView, ComposeViewport), Android TV (tv-material, D-pad, focus, Carousel),
  Material 3 motion, atomic design systems, design-to-code workflows, Paging 3, or
  navigation. Activates Review Mode on GitHub PR URLs and review phrases ("review
  this PR", "what's wrong with this"). Auto-detects Compose projects on
  session_start. Backed by actual androidx/androidx and JetBrains/compose-multiplatform-core
  source receipts. See "## When this skill applies" in SKILL.md for the full trigger
  surface.
version: 2.3.1
---

## When this skill applies

### Compose API mentions
`@Composable`, `remember`, `mutableStateOf`, `derivedStateOf`, `rememberSaveable`,
`LaunchedEffect`, `DisposableEffect`, `SideEffect`, `rememberCoroutineScope`,
`Scaffold`, `NavHost`, `NavController`, `MaterialTheme`, `ColorScheme`,
`Typography`, `LazyColumn`, `LazyRow`, `LazyVerticalGrid`, `HorizontalPager`,
`Modifier`, `Modifier.Node`, `recomposition`, `CompositionLocal`.

### Compose Multiplatform / KMP
`Compose Multiplatform`, `CMP`, `KMP`, `commonMain`, `expect`, `actual`,
`ComposeUIViewController`, `Window` composable, `UIKitView`, `ComposeViewport`,
`Res.drawable`, `Res.string`, `SkikoMain`.

### Design system / design-to-code
`atomic design`, `atoms`, `molecules`, `organisms`, `templates`,
`design tokens`, `design system`, `component library`, `reusable component`,
`Figma to Compose`, `design to compose`, `build this UI`, `implement this design`.

### Casual phrasing
"my compose screen is slow", "my recomposition is broken",
"how do I pass data between screens", "Android UI", "Kotlin UI",
"compose layout", "compose navigation", "compose animation".

## Quick Routing

### State, recomposition, side effects
- **`remember`, `rememberSaveable`, state hoisting** → `state-management`
- **`LaunchedEffect`, `SideEffect`, `DisposableEffect`** → `side-effects`
- **Recomposition frequency, stability, `@Stable`/`@Immutable`** → `performance`
- **`CompositionLocal`, ambient values** → `composition-locals`

### Animation and motion
- **`animate*AsState`, `AnimatedVisibility`, `Crossfade`, `updateTransition`** → `animation`
- **M3 motion tokens, `MotionTokens`, M3 easing curves** → `material3-motion`

### Layout, lists, modifiers
- **`LazyColumn`, `LazyRow`, `LazyVerticalGrid`, sticky headers** → `lists-scrolling`
- **Modifier chain ordering, custom layout, `Modifier.Node`** → `modifiers`

### Navigation
- **`NavHost`, type-safe `@Serializable` routes, nested graphs** → `navigation`
- **Migrating Nav 2 → Nav 3, `NavDisplay`, `NavKey`** → `navigation-migration`

### Theming and design systems
- **`MaterialTheme`, `ColorScheme`, `Typography`, dynamic color, M3 tokens** → `theming-material3`
- **Atom, molecule, organism, template hierarchy, design system structure** → `atomic-design`
- **Figma → Compose, screenshot → composable, design token translation** → `design-to-compose`

### Production and review
- **Production crash, Compose stack trace, `remember` leak** → `production-crash-playbook`
- **PR review, "review this diff", anti-patterns** → `pr-review`
- **Deprecated Compose API, migration from old API** → `deprecated-patterns`

## Workflow

When helping with Compose code:

### 1. Understand the request
- What Compose layer is involved? (Runtime, UI, Foundation, Material3, Navigation)
- Is this a state problem, layout problem, performance problem, or architecture question?
- Is this Android-only or Compose Multiplatform (CMP)?

### 2. Analyze the design (if visual reference provided)
- If the user shares a Figma frame, screenshot, or design spec, decompose the design into a composable tree
- Map design tokens to MaterialTheme, spacing to CompositionLocals
- Identify animation needs

### 3. Apply and verify
- Write code that follows established patterns
- Flag anti-patterns in existing code
- Suggest the minimal correct solution — don't over-engineer

## Key Principles

1. **Compose thinks in three phases**: Composition → Layout → Drawing. State reads in each phase only trigger work for that phase and later ones.

2. **Recomposition is frequent and cheap** — but only if you help the compiler skip unchanged scopes. Use stable types, avoid allocations in composable bodies.

3. **Modifier order matters**. `Modifier.padding(16.dp).background(Color.Red)` is visually different from `Modifier.background(Color.Red).padding(16.dp)`.

4. **State should live as low as possible** and be hoisted only as high as needed. Don't put everything in a ViewModel just because you can.

5. **Side effects exist to bridge Compose's declarative world with imperative APIs**. Use the right one for the job — misusing them causes bugs that are hard to trace.

## Critical Patterns for This Project (Dagboken 4.0)

The project rules go before any generic Compose advice here: skill `shared-ui-components`
(rule 4 – which component or frame, forbidden raw M3 calls), skill `ui-style` (Papper och teal),
skill `accessibility-compose` and, for Navigation 3 recipes, Google's skill `navigation-3`.

This project uses:
- **Material 3 Expressive** via `ui/theme` (`MaterialExpressiveTheme`, `MotionScheme.expressive()`, `AppColors`, `AppTypography`, `AppShapes`, `Spacing`) in the "Papper och teal" look. No colours, shapes, text styles or corner/height dp in feature code. (The 3.x "Sunrise Garden" palette is gone.)
- **Navigation 3**: `@Serializable` `NavKey`, one back stack per tab (`AppBackStack`), `NavDisplay` + `entryProvider`, transitions defined once in `navigation/Transitions`. No `NavHost`/`NavController` (the 3.x navigation-compose string routes are gone).
- **MVVM** with `StateFlow<UiState>` + `collectAsStateWithLifecycle()` and sealed events (`vm.onEvent(...)`).
- **Hilt**: `hiltViewModel()` at screen level; ViewModel scope per nav entry via `lifecycle-viewmodel-navigation3`.
- **No image library** in the UI – icons are line icons in `res/drawable`.

> Expressive and Nav3 APIs are new. Use only names and signatures verified in `VerifiedApisTest`
> (`app/src/test`); never guess an API name.

### State hoisting rule
Screen-level composables take `vm: XViewModel = hiltViewModel()` and hand state to a shared
frame. Child composables take typed state/callback params — never the ViewModel itself.

### Screen structure
```kotlin
@Composable
fun PrescriptionsScreen(vm: PrescriptionsViewModel = hiltViewModel(), onOpen: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    EntityListScreen(
        title = stringResource(R.string.prescriptions_title),
        state = state.list,                                   // ListUiState<Prescription>
        row = { rx -> PausableRow(rx.name, rx.detail, kind = null, active = rx.active, onClick = { onOpen(rx.id) }) },
        add = AddAction(stringResource(R.string.prescriptions_new), { vm.onEvent(PrescriptionsEvent.Add) }),
    )
}
```
Loading, empty, error, add and undo are handled by the frame, not by the screen.

### Errors, loading and buttons
Handled by the shared parts: `DataError.toMessage()` in a snackbar, `AppLoading`,
`AppButton(loading = …)`. Never a hand-rolled `CircularProgressIndicator` in a button or an
error text coloured with `colorScheme.error`.

### Cards and sections
`AppCard` + `SectionHeader` for section cards; post cards follow NFR-15/16 (the shared post card
is ported in etapp 4). Never `Card`/`ElevatedCard` + `HorizontalDivider` with own padding.

## Anti-Patterns to Avoid

- Never call `remember` inside conditional blocks or loops
- Never read `StateFlow.value` in a composable — always use `collectAsStateWithLifecycle()`
- Never pass `Context` to a ViewModel constructor — use `@ApplicationContext` or `activityContext` passed from the composable for operations that need it
- Never mutate the Navigation 3 back stack from a ViewModel — use callbacks or effects
- `LaunchedEffect(Unit)` for one-time setup; `LaunchedEffect(key)` when the effect should re-run on key change
- `DisposableEffect` for resources that need cleanup (listeners, subscriptions)
- Use `rememberLauncherForActivityResult` for Activity Result API — never call `startActivityForResult` directly
