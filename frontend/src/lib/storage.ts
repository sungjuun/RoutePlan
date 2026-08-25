import type { WorkspaceReference } from '../types'

const KEY = 'routeplan.workspace.v1'

export function loadWorkspace(): WorkspaceReference {
  try {
    const value = localStorage.getItem(KEY)
    if (!value) return { user: null, tripId: null }
    const parsed = JSON.parse(value) as WorkspaceReference
    return {
      user: parsed.user ?? null,
      tripId: typeof parsed.tripId === 'number' ? parsed.tripId : null,
    }
  } catch {
    return { user: null, tripId: null }
  }
}

export function saveWorkspace(value: WorkspaceReference): void {
  localStorage.setItem(KEY, JSON.stringify(value))
}

export function clearTripReference(user: WorkspaceReference['user']): void {
  saveWorkspace({ user, tripId: null })
}
