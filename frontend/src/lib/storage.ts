import type { WorkspaceReference } from '../types'

const KEY = 'routeplan.workspace.v1'

export function loadWorkspace(): WorkspaceReference {
  try {
    const value = localStorage.getItem(KEY)
    if (!value) return { tripId: null }
    const parsed = JSON.parse(value) as WorkspaceReference
    return {
      tripId: typeof parsed.tripId === 'number' ? parsed.tripId : null,
    }
  } catch {
    return { tripId: null }
  }
}

export function saveWorkspace(value: WorkspaceReference): void {
  localStorage.setItem(KEY, JSON.stringify(value))
}

export function clearTripReference(): void {
  saveWorkspace({ tripId: null })
}
