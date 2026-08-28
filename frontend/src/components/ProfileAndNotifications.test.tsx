import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { advanced } from '../api/advanced'
import { NotificationCenter } from './NotificationCenter'
import { ProfileImageEditor } from './ProfileImageEditor'
import { WeatherAutoRefresh } from './WeatherAutoRefresh'

const user = { id: 8, nickname: '사진 여행자', email: 'test@example.com', createdAt: '2026-01-01T00:00:00Z' }
describe('profile image and quiet notifications', () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals() })
  it('keeps notifications hidden until opened, supports read, clear and Escape', () => {
    const onRead = vi.fn(), onClear = vi.fn()
    render(<NotificationCenter items={[{ id: 1, kind: 'success', message: '여행을 저장했습니다.', read: false, time: '2026-09-10T00:00:00Z' }]} onRead={onRead} onClear={onClear} />)
    expect(screen.queryByText('여행을 저장했습니다.')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '알림 1개 읽지 않음' }))
    expect(screen.getByText('여행을 저장했습니다.')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: '모두 읽음' })); expect(onRead).toHaveBeenCalledOnce()
    fireEvent.click(screen.getByRole('button', { name: '전체 비우기' })); expect(onClear).toHaveBeenCalledOnce()
    fireEvent.keyDown(document, { key: 'Escape' }); expect(screen.queryByRole('region', { name: '알림함' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '알림 1개 읽지 않음' })).toHaveFocus()
  })
  it('validates uploads before sending, previews and saves, and releases the preview URL', async () => {
    const revoke = vi.fn()
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:photo-preview'), revokeObjectURL: revoke }))
    const upload = vi.spyOn(api, 'uploadProfileImage').mockResolvedValue({ profileImageUrl: '/api/v1/profile/avatar?v=1' })
    const changed = vi.fn()
    render(<ProfileImageEditor user={user} onChanged={changed} />)
    const input = screen.getByLabelText('프로필 사진 파일')
    fireEvent.change(input, { target: { files: [new File(['<svg/>'], 'photo.svg', { type: 'image/svg+xml' })] } })
    expect(screen.getByRole('alert')).toHaveTextContent('PNG'); expect(upload).not.toHaveBeenCalled()
    const file = new File(['png'], 'photo.png', { type: 'image/png' })
    fireEvent.change(input, { target: { files: [file] } })
    expect(await screen.findByAltText('새 프로필 사진 미리보기')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: '사진 저장' }))
    await waitFor(() => expect(changed).toHaveBeenCalledWith({ ...user, profileImageUrl: '/api/v1/profile/avatar?v=1' }))
    expect(upload).toHaveBeenCalledWith(file); expect(revoke).toHaveBeenCalledWith('blob:photo-preview')
  })
  it('resets to default without changing other user fields', async () => {
    vi.spyOn(api, 'removeProfileImage').mockResolvedValue({ profileImageUrl: null })
    const changed = vi.fn()
    render(<ProfileImageEditor user={{ ...user, profileImageUrl: '/api/v1/profile/avatar?v=2' }} onChanged={changed} />)
    fireEvent.click(screen.getByRole('button', { name: '기본 이미지' }))
    await waitFor(() => expect(changed).toHaveBeenCalledWith({ ...user, profileImageUrl: null }))
  })
  it('loads and toggles server-side weather refresh explicitly', async () => {
    const settings = { enabled: false, nextRefreshAt: null, lastSuccessAt: null, lastError: null }
    vi.spyOn(advanced, 'weatherRefreshSettings').mockResolvedValue(settings)
    const save = vi.spyOn(advanced, 'saveWeatherRefreshSettings').mockResolvedValue({ ...settings, enabled: true })
    render(<WeatherAutoRefresh tripId={11} />)
    const checkbox = screen.getByRole('checkbox', { name: '날씨를 3시간마다 자동 갱신' })
    await waitFor(() => expect(checkbox).toBeEnabled())
    fireEvent.click(checkbox)
    await waitFor(() => expect(save).toHaveBeenCalledWith(11, true))
    expect(await screen.findByRole('status')).toHaveTextContent('자동 갱신 켜짐')
    expect(checkbox).toBeChecked()
  })
  it('rolls back a failed weather setting without claiming it was saved', async () => {
    vi.spyOn(advanced, 'weatherRefreshSettings').mockResolvedValue({ enabled: false, nextRefreshAt: null, lastSuccessAt: null, lastError: null })
    vi.spyOn(advanced, 'saveWeatherRefreshSettings').mockRejectedValue(new Error('offline'))
    render(<WeatherAutoRefresh tripId={11} />)
    const checkbox = screen.getByRole('checkbox')
    await waitFor(() => expect(checkbox).toBeEnabled())
    fireEvent.click(checkbox)
    expect(await screen.findByRole('alert')).toHaveTextContent('저장에 실패')
    expect(checkbox).not.toBeChecked()
  })
})
