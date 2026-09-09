# Frontend architecture

Conventions for the BillBuddy mobile app (Expo / React Native / TypeScript). This is the frontend's equivalent of the backend's `CODING_STANDARDS.md` — read this before adding a new screen, component, or API call.

## Stack

- **Expo + React Native + TypeScript**
- **Navigation**: `@react-navigation/native` with native-stack navigators
- **State**: `zustand`, one store per domain (e.g. `authStore`)
- **Networking**: `axios`, one shared client with typed wrapper functions per backend feature area
- **Styling**: `nativewind` (Tailwind CSS for React Native) — no `StyleSheet.create`, no hardcoded hex colors in component files
- **Secure storage**: `expo-secure-store` for tokens; `@react-native-async-storage/async-storage` for non-sensitive persisted values (device id, UI preferences)

## Folder structure

```
src/
  api/          one file per backend feature area (auth.ts, groups.ts, ...),
                typed request/response, thin wrappers over the shared client.ts
  components/
    atoms/      smallest reusable primitives (Button, TextField, Logo) — no business logic
    molecules/  small compositions of atoms with light logic (AuthCard, form groups)
    organisms/  larger, feature-specific compositions (a full expense list, a group card)
  config/       env.ts and other static configuration
  hooks/        shared custom hooks
  navigation/   one navigator per flow (AuthNavigator, AppNavigator) + RootNavigator
                that switches between them based on auth state
  screens/      one file per screen, suffixed `*Screen.tsx`, wires api + store + components
  store/        one Zustand store per domain, suffixed `*Store.ts`
  types/        shared TypeScript types not tied to one api/ file
  utils/        pure helper functions (deviceId, error message extraction, etc.)
```

A screen should stay thin: local form state, calling `api/*` functions, reading/writing a `store/*Store`, and composing `components/*`. Business logic that isn't purely presentational belongs in `api/` or `store/`, not inline in a screen.

## Naming conventions

- Components and their files: `PascalCase.tsx` (`Button.tsx`, `LoginScreen.tsx`)
- Everything else (functions, variables, hooks): `camelCase`
- Screens: `*Screen.tsx`
- Stores: `*Store.ts`, exported hook named `use*Store` (e.g. `useAuthStore`)
- API modules: one per backend feature area, named after it (`auth.ts`, `groups.ts`), matching `apps/backend/docs/*.md` naming

## API layer pattern

`src/api/client.ts` holds the single shared Axios instance (base URL from `src/config/env.ts`, a request interceptor that attaches the current access token from `authStore`). Every other file under `src/api/` exports typed async functions (`signup()`, `login()`, ...) that call `apiClient` and return a typed response — screens never call `apiClient` or `axios` directly. Request/response types are copied from the corresponding `apps/backend/docs/*.md` file, not guessed — check the doc before adding a new call.

## State management pattern

One Zustand store per domain (`authStore` today; a future `groupsStore` would be its own file). Stores own their persistence: `authStore` is the only thing that talks to `expo-secure-store`, exposing `setSession` / `clearSession` / `hydrate` actions rather than letting screens read/write secure storage directly.

## Styling convention

Use NativeWind's `className` prop, not `StyleSheet.create`. Colors come from the theme defined in `tailwind.config.js` (`primary`, `primary-tint`, `primary-dark` today) — never a hardcoded hex value in a component file. If a new color is genuinely needed, add it to the theme first.

## Error handling

API errors from the backend come back as `{ error, message, timestamp }` (see any `apps/backend/docs/*.md` "Errors specific to ..." table for the exact codes). Use `src/utils/errors.ts`'s `getErrorMessage()` to extract a user-facing message from a caught Axios error rather than parsing `error.response.data` inline in a screen.
