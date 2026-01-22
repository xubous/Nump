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
// BACKEND CHECK
// =====================================================
const API = "http://127.0.0.1:8080";

async function aguardarServidor() {
    for (let i = 0; i < 20; i++) {
        try {
            const r = await fetch(`${API}/api/hello`);
            if (r.ok) {
                console.log("[OK] Backend ativo");
                return;
            }
        } catch {}
        await new Promise(r => setTimeout(r, 500));
    }
    console.error("[ERRO] Backend não iniciou");
}

document.addEventListener("DOMContentLoaded", aguardarServidor);

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
    li.innerHTML = `${file.name} ⏳`;
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
            li.innerHTML = `${file.name} ✓`;
        } else {
            li.innerHTML = `${file.name} ✗`;
        }
    } catch (error) {
        console.error("[ERROR] Upload falhou:", error);
        li.innerHTML = `${file.name} ✗`;
    }
}