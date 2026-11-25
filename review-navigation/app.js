const status = document.querySelector('#copy-status')
let hideStatus

function showStatus(message) {
  clearTimeout(hideStatus)
  status.textContent = message
  status.hidden = false
  hideStatus = setTimeout(() => { status.hidden = true }, 3500)
}

document.querySelectorAll('button[data-copy]').forEach(button => {
  button.addEventListener('click', async () => {
    const value = button.dataset.copy
    try {
      await navigator.clipboard.writeText(value)
      showStatus(`已复制账号 ${value}`)
    } catch {
      const text = button.previousElementSibling
      const selection = window.getSelection()
      if (selection && text) {
        const range = document.createRange()
        range.selectNodeContents(text)
        selection.removeAllRanges()
        selection.addRange(range)
      }
      showStatus('已选中账号，按 Ctrl+C 或 ⌘C 复制。')
    }
  })
})
