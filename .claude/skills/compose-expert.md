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

## Critical Patterns for This Project

This project uses:
- **Material 3** with a warm "Sunrise Garden" palette (Amber, Rose, Emerald color tokens)
- **Navigation Compose** with type-safe `@Serializable` routes
- **MVVM** with `StateFlow<UiState>` + `collectAsStateWithLifecycle()`
- **Hilt** for DI, `hiltViewModel()` in composables
- **Coil** for image loading (`AsyncImage`)
- **`collectAsState()`** — prefer `collectAsStateWithLifecycle()` for lifecycle safety

### State hoisting rule
Screen-level composables (`HomeScreen`, `SettingsScreen`, etc.) take `vm: ViewModel = hiltViewModel()`. Child composables take typed state/callback params — never the ViewModel itself.

### Error display pattern
```kotlin
state.errorMessage?.let { err ->
    Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
```

### Loading button pattern
```kotlin
Button(onClick = { vm.doAction() }, enabled = !state.isLoading) {
    if (state.isLoading) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
    }
    Text("Label")
}
```

### Card sections pattern (used throughout this app)
```kotlin
ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Section title", style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        // content
    }
}
```

## Anti-Patterns to Avoid

- Never call `remember` inside conditional blocks or loops
- Never read `StateFlow.value` in a composable — always use `collectAsStateWithLifecycle()`
- Never pass `Context` to a ViewModel constructor — use `@ApplicationContext` or `activityContext` passed from the composable for operations that need it
- Never put navigation logic inside the ViewModel — use callbacks or effects
- `LaunchedEffect(Unit)` for one-time setup; `LaunchedEffect(key)` when the effect should re-run on key change
- `DisposableEffect` for resources that need cleanup (listeners, subscriptions)
- Use `rememberLauncherForActivityResult` for Activity Result API — never call `startActivityForResult` directly
