import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  timeout: 600000
})

function shortToast(msg) {
  const text = String(msg || '请求失败')
  // 长错误明细应落在页面结果区，弹窗只提示摘要，避免挡住整页无法操作
  if (text.length > 180) {
    return text.slice(0, 180) + '…（详见页面结果）'
  }
  return text
}

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body.code === 'number' && body.code !== 200) {
      ElMessage.error(shortToast(body.message || '请求失败'))
      return Promise.reject(body)
    }
    return body
  },
  (error) => {
    const msg = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(shortToast(msg))
    return Promise.reject(error)
  }
)

export default http
