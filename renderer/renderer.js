// =====================================================
// CONFIGURAÇÃO DA API
// =====================================================
const API = "http://localhost:8080";

// =====================================================
// 🔴 BLOQUEIO GLOBAL DE DRAG (OBRIGATÓRIO NO ELECTRON)
// =====================================================
window.addEventListener("dragover", e => {
    e.preventDefault();
});

window.addEventListener("drop", e => {
    e.preventDefault();
});

// =====================================================
// CONTROLES DA JANELA
// =====================================================
document.getElementById("minimize-btn")?.addEventListener("click", () => {
    window.electron?.minimize();
});

document.getElementById("maximize-btn")?.addEventListener("click", () => {
    window.electron?.toggleMaximize();
});

document.getElementById("close-btn")?.addEventListener("click", () => {
    window.electron?.close();
});

// =====================================================
// TO-DO
// =====================================================
const input = document.getElementById("text-task-todo");
const button = document.getElementById("send-task-todo");
const list = document.getElementById("list-items-todo");

button.onclick = () => {
    const text = input.value.trim();
    if (!text) return;

    const li = document.createElement("li");
    li.className = "item-todo";

    const p = document.createElement("p");
    p.textContent = text;

    const del = document.createElement("button");
    del.textContent = "Excluir";
    del.onclick = () => li.remove();

    li.append(p, del);
    list.appendChild(li);
    input.value = "";
};

// =====================================================
// POMODORO
// =====================================================
const init = document.getElementById("init-timer");
const reset = document.getElementById("reset-timer");
const h = document.getElementById("hours");
const m = document.getElementById("minutes");
const s = document.getElementById("seconds");

let total = 0;
let timer = null;

init.onclick = () => {
    if (timer) {
        clearInterval(timer);
        timer = null;
        init.textContent = "Iniciar";
        return;
    }

    total =
        Number(h.value) * 3600 +
        Number(m.value) * 60 +
        Number(s.value);

    if (total <= 0) return;

    init.textContent = "Pausar";

    timer = setInterval(() => {
        total--;
        h.value = String(Math.floor(total / 3600)).padStart(2, "0");
        m.value = String(Math.floor((total % 3600) / 60)).padStart(2, "0");
        s.value = String(total % 60).padStart(2, "0");

        if (total <= 0) {
            clearInterval(timer);
            timer = null;
            init.textContent = "Iniciar";
            alert("Pomodoro finalizado");
        }
    }, 1000);
};

reset.onclick = () => {
    clearInterval(timer);
    timer = null;
    h.value = m.value = s.value = "00";
    init.textContent = "Iniciar";
};

// =====================================================
// FILES — DRAG & DROP REAL (FUNCIONA NO ELECTRON)
// =====================================================
const dropArea = document.getElementById("drop-area");
const fileInput = document.getElementById("file-input");
const filesList = document.getElementById("files-list");
const loadBtn = document.getElementById("load-files-btn");
const selectBtn = document.getElementById("select-files-btn");

["dragenter", "dragover"].forEach(e =>
    dropArea.addEventListener(e, ev => {
        ev.preventDefault();
        ev.stopPropagation();
        dropArea.style.borderColor = "#4CAF50";
    })
);

["dragleave", "drop"].forEach(e =>
    dropArea.addEventListener(e, ev => {
        ev.preventDefault();
        ev.stopPropagation();
        dropArea.style.borderColor = "white";
    })
);

dropArea.addEventListener("drop", e => {
    const files = e.dataTransfer.files;
    [...files].forEach(uploadFile);
});

selectBtn.onclick = () => fileInput.click();
fileInput.onchange = e => [...e.target.files].forEach(uploadFile);

// Abre a página web no navegador padrão
loadBtn.onclick = () => {
    window.electron?.openExternal('http://localhost:8080');
};

// =====================================================
// UPLOAD
// =====================================================
async function uploadFile(file) {
    const li = document.createElement("li");
    const container = document.createElement("div");
    container.className = "file-item-container";
    
    const status = document.createElement("div");
    status.className = "file-status";
    status.innerHTML = `<span>${file.name}</span> <span>⏳</span>`;
    
    container.appendChild(status);
    li.appendChild(container);
    filesList.appendChild(li);

    const fd = new FormData();
    fd.append("files", file);

    try {
        const r = await fetch(`${API}/api/upload`, {
            method: "POST",
            body: fd
        });
        const d = await r.json();
        
        if (d.success) {
            // Obter IP local da rede
            const ipResponse = await fetch(`${API}/api/network-info`);
            const data = await ipResponse.json();
            
            // Usar o localIP que retorna o endereço 192.168.0.x
            const localIP = data.localIP || data.ip;
            const downloadUrl = `http://${localIP}:8080/download/${encodeURIComponent(file.name)}`;
            
            status.innerHTML = `<span>${file.name}</span> <span>✓</span>`;
            
            const linkDiv = document.createElement("div");
            linkDiv.className = "file-link";
            linkDiv.textContent = downloadUrl;
            linkDiv.title = "Clique para copiar";
            linkDiv.onclick = () => {
                navigator.clipboard.writeText(downloadUrl).then(() => {
                    const originalText = linkDiv.textContent;
                    linkDiv.textContent = "✓ Copiado!";
                    setTimeout(() => {
                        linkDiv.textContent = originalText;
                    }, 1500);
                });
            };
            
            container.appendChild(linkDiv);
        } else {
            status.innerHTML = `<span>${file.name}</span> <span>✗</span>`;
        }
    } catch (error) {
        console.error("[ERROR] Upload falhou:", error);
        status.innerHTML = `<span>${file.name}</span> <span>✗</span>`;
    }
}
