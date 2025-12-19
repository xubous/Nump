import http from "http";
import fs from "fs";
import path from "path";

const uploadDir = "./uploads";

if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir);
}

http.createServer((req, res) => {
  const parsedUrl = new URL(req.url, `http://${req.headers.host}`);

  // CORS
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "X-Filename, Content-Type");

  if (req.method === "OPTIONS") {
    res.writeHead(200);
    res.end();
    return;
  }

  // Upload
  if (req.method === "POST" && parsedUrl.pathname === "/upload") {
    const filename = req.headers["x-filename"] || "file.bin";
    const filepath = path.join(uploadDir, filename);

    const stream = fs.createWriteStream(filepath);
    req.pipe(stream);

    req.on("end", () => {
      res.writeHead(200);
      res.end("OK");
    });
    return;
  }

  // Listar arquivos
  if (req.method === "GET" && parsedUrl.pathname === "/files") {
    const files = fs.readdirSync(uploadDir);
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify(files));
    return;
  }

  // Download
  if (req.method === "GET" && parsedUrl.pathname.startsWith("/download/")) {
    const filename = decodeURIComponent(parsedUrl.pathname.replace("/download/", ""));
    const filepath = path.join(uploadDir, filename);

    if (fs.existsSync(filepath)) {
      res.writeHead(200, { "Content-Disposition": `attachment; filename="${filename}"` });
      fs.createReadStream(filepath).pipe(res);
    } else {
      res.writeHead(404);
      res.end("Arquivo não encontrado");
    }
    return;
  }

  res.writeHead(404);
  res.end("Not Found");
}).listen(8080, () => {
  console.log("Servidor ativo em http://localhost:8080");
});
