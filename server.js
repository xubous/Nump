const express = require("express");
const multer = require("multer");
const fs = require("fs");
const path = require("path");
const cors = require("cors");
const { app: electronApp } = require("electron");

const app = express();
const PORT = 8080;

// =====================================
// DIRETÓRIO CORRETO (Electron)
// =====================================
let uploadDir;

// Função para obter o diretório de dados
function getDataDir() {
    // Durante o desenvolvimento, usa um diretório local
    if (!electronApp.isReady()) {
        return path.join(__dirname, "uploads");
    }
    const DATA_DIR = electronApp.getPath("userData");
    return path.join(DATA_DIR, "uploads");
}

// Função para obter IP local
function getLocalIP() {
    const networkInterfaces = require('os').networkInterfaces();
    
    // Primeiro tenta encontrar IP 192.168.x.x
    for (const interfaceName in networkInterfaces) {
        for (const iface of networkInterfaces[interfaceName]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                // Prioriza IPs que começam com 192.168
                if (iface.address.startsWith('192.168.')) {
                    return iface.address;
                }
            }
        }
    }
    
    // Se não encontrar 192.168, retorna qualquer IP não-interno
    for (const interfaceName in networkInterfaces) {
        for (const iface of networkInterfaces[interfaceName]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                return iface.address;
            }
        }
    }
    
    return 'localhost';
}

// =====================================
// MIDDLEWARES
// =====================================
app.use(cors({
    origin: "*",
    methods: ["GET", "POST", "DELETE"],
    allowedHeaders: ["Content-Type"]
}));
app.use(express.json());

// =====================================
// UPLOADS
// =====================================
function initializeUploadDir() {
    uploadDir = getDataDir();
    
    if (!fs.existsSync(uploadDir)) {
        fs.mkdirSync(uploadDir, { recursive: true });
        console.log("[DEBUG] Pasta uploads criada:", uploadDir);
    } else {
        console.log("[DEBUG] Pasta uploads já existe:", uploadDir);
    }
}

const storage = multer.diskStorage({
    destination: (_, __, cb) => cb(null, uploadDir),
    filename: (_, file, cb) => cb(null, file.originalname)
});

const upload = multer({ storage });

// =====================================
// PÁGINA PRINCIPAL
// =====================================
app.get("/", (_, res) => {
    try {
        const files = fs.readdirSync(uploadDir).map(file => {
            const stats = fs.statSync(path.join(uploadDir, file));
            return { name: file, size: stats.size };
        });

        res.send(`
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Nump - Arquivos Compartilhados</title>
    <style>
        body { font-family: Arial; margin: 40px; background: #1a1a1a; color: white; }
        h1 { color: #4CAF50; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #333; }
        th { background: #2d2d2d; }
        a { color: #4CAF50; margin-right: 15px; text-decoration: none; }
        a:hover { text-decoration: underline; }
        .empty { text-align: center; padding: 40px; color: #666; }
    </style>
</head>
<body>
    <h1>📁 Arquivos Compartilhados</h1>
    ${files.length === 0 ? 
        '<p class="empty">Nenhum arquivo disponível. Arraste arquivos no app Nump para compartilhar.</p>' :
        `<table>
            <tr>
                <th>Arquivo</th>
                <th>Tamanho</th>
                <th>Ações</th>
            </tr>
            ${files.map(f => {
                const sizeKB = (f.size / 1024).toFixed(2);
                return `
                    <tr>
                        <td>${f.name}</td>
                        <td>${sizeKB} KB</td>
                        <td>
                            <a href="/download/${encodeURIComponent(f.name)}" download>Baixar</a>
                            <a href="/delete/${encodeURIComponent(f.name)}" onclick="return confirm('Excluir ${f.name}?')">Excluir</a>
                        </td>
                    </tr>
                `;
            }).join('')}
        </table>`
    }
    <script>
        setTimeout(() => location.reload(), 5000);
    </script>
</body>
</html>
        `);
    } catch (error) {
        res.send('<h1>Erro ao carregar arquivos</h1>');
    }
});

// Nova rota para obter IP da rede
app.get("/api/network-info", (_, res) => {
    const localIP = getLocalIP();
    res.json({ 
        localIP: localIP,  // IP da rede local (192.168.0.x)
        ip: localIP        // Mantém compatibilidade
    });
});

app.post("/api/upload", upload.single("files"), (req, res) => {
    if (!req.file) {
        console.error("[ERROR] Nenhum arquivo recebido");
        return res.status(400).json({ success: false });
    }
    console.log("[DEBUG] Upload:", req.file.originalname, req.file.size);
    res.json({
        success: true,
        filename: req.file.originalname,
        size: req.file.size
    });
});

app.get("/api/files", (_, res) => {
    try {
        const files = fs.readdirSync(uploadDir).map(file => {
            const stats = fs.statSync(path.join(uploadDir, file));
            return { name: file, size: stats.size };
        });
        res.json({ files });
    } catch (error) {
        console.error("[ERROR] Erro ao listar arquivos:", error);
        res.json({ files: [] });
    }
});

// DOWNLOAD DIRETO
app.get("/download/:filename", (req, res) => {
    const filePath = path.join(uploadDir, req.params.filename);
    if (!fs.existsSync(filePath)) {
        return res.status(404).send("Arquivo não encontrado");
    }
    res.download(filePath);
});

// DELETE VIA GET (para funcionar em links)
app.get("/delete/:filename", (req, res) => {
    const filePath = path.join(uploadDir, req.params.filename);
    if (!fs.existsSync(filePath)) {
        return res.status(404).send("Arquivo não encontrado");
    }
    fs.unlinkSync(filePath);
    console.log("[DEBUG] Arquivo deletado:", req.params.filename);
    res.redirect('/');
});

// Manter rotas antigas para compatibilidade
app.get("/api/files/:filename/download", (req, res) => {
    const filePath = path.join(uploadDir, req.params.filename);
    if (!fs.existsSync(filePath)) {
        return res.status(404).send("Arquivo não encontrado");
    }
    res.download(filePath);
});

app.delete("/api/files/:filename", (req, res) => {
    const filePath = path.join(uploadDir, req.params.filename);
    if (!fs.existsSync(filePath)) {
        return res.status(404).send("Arquivo não encontrado");
    }
    fs.unlinkSync(filePath);
    console.log("[DEBUG] Arquivo deletado:", req.params.filename);
    res.json({ success: true });
});

// =====================================
// START (CONTROLADO)
// =====================================
let started = false;

function startServer() {
    if (started) return;
    started = true;
    
    initializeUploadDir();
    
    // 🔥 ESCUTA EM TODAS AS INTERFACES DE REDE (0.0.0.0)
    app.listen(PORT, '0.0.0.0', () => {
        console.log(`\n${'='.repeat(50)}`);
        console.log("📡 NUMP - COMPARTILHAMENTO DE ARQUIVOS");
        console.log('='.repeat(50));
        console.log(`✓ Servidor rodando na porta ${PORT}`);
        console.log(`✓ Pasta de uploads: ${uploadDir}`);
        console.log('');
        
        // Mostra o IP local para compartilhamento
        const networkInterfaces = require('os').networkInterfaces();
        console.log("🌐 ACESSE DE OUTROS DISPOSITIVOS:");
        console.log(`   Local: http://localhost:${PORT}`);
        
        Object.keys(networkInterfaces).forEach(interfaceName => {
            networkInterfaces[interfaceName].forEach(iface => {
                if (iface.family === 'IPv4' && !iface.internal) {
                    console.log(`   Rede:  http://${iface.address}:${PORT}`);
                }
            });
        });
        console.log('='.repeat(50) + '\n');
    });
}

module.exports = { startServer };
