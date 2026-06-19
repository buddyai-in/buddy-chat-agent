/* ============================================================
   chat.js — BuddyAI Chat Playground
   ============================================================ */

class ChatPlayground {
  constructor(clientId) {
    this.clientId = clientId;
    this.sessionId = 'playground-' + crypto.randomUUID();
    this.messages = [];
    this.isLoading = false;

    this.messagesEl  = document.getElementById('chat-messages');
    this.inputEl     = document.getElementById('chat-input');
    this.sendBtn     = document.getElementById('send-btn');
    this.sessionEl   = document.getElementById('session-id-display');
    this.toolsEl     = document.getElementById('tools-used-display');
    this.durationEl  = document.getElementById('duration-display');
    this.agentEl     = document.getElementById('agent-display');
    this.modelEl     = document.getElementById('model-display');
    this.msgCountEl  = document.getElementById('msg-count-display');

    if (this.sessionEl) this.sessionEl.textContent = this.sessionId;

    // Bind events
    this.sendBtn?.addEventListener('click', () => this.sendMessage());
    this.inputEl?.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); this.sendMessage(); }
    });
    this.inputEl?.addEventListener('input', () => {
      this.inputEl.style.height = 'auto';
      this.inputEl.style.height = Math.min(this.inputEl.scrollHeight, 120) + 'px';
    });

    document.getElementById('new-session-btn')?.addEventListener('click', () => this.newSession());
    document.getElementById('clear-chat-btn')?.addEventListener('click', () => this.clearChat());

    // Quick prompts
    document.querySelectorAll('.quick-prompt').forEach((btn) => {
      btn.addEventListener('click', () => {
        const prompt = btn.getAttribute('data-prompt');
        if (prompt && this.inputEl) {
          this.inputEl.value = prompt;
          this.inputEl.focus();
        }
      });
    });

    this._updateMsgCount();
  }

  async sendMessage() {
    const text = this.inputEl?.value?.trim();
    if (!text || this.isLoading) return;

    this.inputEl.value = '';
    this.inputEl.style.height = 'auto';
    this.addMessage('user', text);
    this.setLoading(true);

    const startTime = Date.now();

    try {
      const res = await fetch(`/v1/agent/${encodeURIComponent(this.clientId)}/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Session-Id': this.sessionId,
          'X-User-Id': 'playground-user',
          'X-Channel': 'REST'
        },
        body: JSON.stringify({ message: text })
      });

      if (!res.ok) {
        const errText = await res.text().catch(() => '');
        throw new Error(`HTTP ${res.status}${errText ? ': ' + errText : ''}`);
      }

      const data = await res.json();
      const elapsed = Date.now() - startTime;

      this.addMessage('assistant', data.message || data.response || '(no response)');

      // Update sidebar info
      if (this.toolsEl) {
        const tools = data.toolCallsUsed?.map(t => t.toolName || t).join(', ') || 'None';
        this.toolsEl.textContent = tools;
      }
      if (this.durationEl) {
        this.durationEl.textContent = formatDuration(data.durationMs ?? elapsed);
      }
      if (this.agentEl && data.agentName) {
        this.agentEl.textContent = data.agentName;
      }
      if (this.modelEl && data.model) {
        this.modelEl.textContent = data.model;
      }
    } catch (e) {
      this.addMessage('assistant', '⚠️ Error: ' + e.message, true);
    } finally {
      this.setLoading(false);
    }
  }

  addMessage(role, text, isError) {
    isError = isError || false;
    const div = document.createElement('div');
    div.className = 'message ' + role + (isError ? ' error' : '');

    const timeStr = new Date().toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
    const senderLabel = role === 'user' ? 'You' : 'BuddyAI';

    div.innerHTML = `
      <div class="message-bubble">${this._renderText(text)}</div>
      <div class="message-meta">${senderLabel} · ${timeStr}</div>
    `;

    this.messagesEl?.appendChild(div);
    this._scrollToBottom();
    this.messages.push({ role, text, time: Date.now() });
    this._updateMsgCount();
  }

  _renderText(text) {
    // Escape HTML then re-apply safe formatting
    const escaped = text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');

    // Convert newlines to <br> and wrap code backticks
    return escaped
      .replace(/`([^`]+)`/g, '<code style="font-family:var(--font-mono);font-size:.9em;background:rgba(0,0,0,.12);padding:1px 5px;border-radius:3px">$1</code>')
      .replace(/\n/g, '<br>');
  }

  setLoading(loading) {
    this.isLoading = loading;

    if (this.sendBtn) {
      this.sendBtn.disabled = loading;
      this.sendBtn.innerHTML = loading
        ? '<span class="spinner-sm"></span>'
        : '<i class="bi bi-send-fill"></i>';
    }

    document.getElementById('typing-indicator')?.remove();

    if (loading) {
      const typing = document.createElement('div');
      typing.className = 'message assistant typing-indicator';
      typing.id = 'typing-indicator';
      typing.innerHTML = '<div class="message-bubble"><span></span><span></span><span></span></div>';
      this.messagesEl?.appendChild(typing);
      this._scrollToBottom();
    }
  }

  newSession() {
    this.sessionId = 'playground-' + crypto.randomUUID();
    if (this.sessionEl) this.sessionEl.textContent = this.sessionId;
    this.clearChat();
    showToast('New session started', 'success');
  }

  clearChat() {
    if (this.messagesEl) {
      this.messagesEl.innerHTML = '';
      this._addWelcomeMessage();
    }
    this.messages = [];
    if (this.toolsEl) this.toolsEl.textContent = '-';
    if (this.durationEl) this.durationEl.textContent = '-';
    this._updateMsgCount();
  }

  _addWelcomeMessage() {
    const div = document.createElement('div');
    div.className = 'message assistant';
    div.innerHTML = `
      <div class="message-bubble">
        👋 Hello! I'm BuddyAI. Ask me anything — I can use tools, search databases, call APIs, and more. How can I help you today?
      </div>
      <div class="message-meta">BuddyAI · Now</div>
    `;
    this.messagesEl?.appendChild(div);
  }

  _scrollToBottom() {
    if (this.messagesEl) {
      this.messagesEl.scrollTo({ top: this.messagesEl.scrollHeight, behavior: 'smooth' });
    }
  }

  _updateMsgCount() {
    if (this.msgCountEl) {
      this.msgCountEl.textContent = this.messages.length + ' messages';
    }
  }
}
