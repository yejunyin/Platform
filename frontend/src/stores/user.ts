import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface UserInfo {
  id: number
  username: string
  realName: string
  avatar: string
  email: string
  phone: string
  deptId: number
  deptName: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo>({
    id: 1,
    username: 'admin',
    realName: '系统管理员',
    avatar: '',
    email: 'admin@eb.com',
    phone: '13800000000',
    deptId: 1,
    deptName: '集团总部',
  })

  const setToken = (val: string) => {
    token.value = val
    localStorage.setItem('token', val)
  }

  const setUserInfo = (val: UserInfo) => {
    userInfo.value = val
    localStorage.setItem('userInfo', JSON.stringify(val))
  }

  const logout = () => {
    token.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  return {
    token,
    userInfo,
    setToken,
    setUserInfo,
    logout,
  }
})
