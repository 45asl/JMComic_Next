#!/usr/bin/env python3
"""选择性地让某些主机的请求失败，其余正常转发 —— 用来验证「网络出问题时会怎样」。

为什么需要它：像「某一页图片加载失败」「列表续加失败」这类路径，只有让**部分**主机失败
才做得出来（API 通、图床断）。试过的三条路都不通：

  - 把系统代理指向死端口：只能整体断网，API 也一起挂，进不了阅读页
  - 系统代理的排除名单：Android 上不生效（试过 `global_http_proxy_exclusion_list`）
  - iptables 按目的地址丢包：这台设备的 ROM 上 `-d <ip>` 规则会**静默丢目标**，
    建出来是一条没有 `-j` 的空规则（`-p tcp --dport` 却正常），排查成本高于收益

所以用这个几十行的 HTTP CONNECT 代理：
命中 BLOCK 列表的主机直接断开连接（客户端看到的就是连接失败），其余正常隧道转发。

用法（本机起代理 → 设备侧把系统代理指过来 → 用 `adb reverse` 绕开局域网可达性）：

    BLOCK_HOSTS="jmapiproxy,jmdanjonproxy" python3 flaky_cdn_proxy.py 8888
    adb -s <设备> reverse tcp:8888 tcp:8888
    adb -s <设备> shell settings put global http_proxy 127.0.0.1:8888
    # ……验证失败路径……
    adb -s <设备> shell settings put global http_proxy :0     # 恢复
    adb -s <设备> reverse --remove-all

两个注意点：代理是**系统级**的，设备上所有应用的流量都会经过它（日志里会看到别的应用）；
清代理必须用 `http_proxy :0`，`settings delete global http_proxy` 会留下
`global_http_proxy_host/port` 两把键，代理依然生效（我踩过）。
"""
import os
import socket
import sys
import threading

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8888
# 命中即失败的主机片段（图床域名有多种，都被这个代理挡下）
BLOCK = tuple(
    host for host in os.environ.get(
        "BLOCK_HOSTS", "jmapiproxy,jmdanjonproxy,media.photos"
    ).split(",") if host
)
BUFFER = 65536


def pipe(a: socket.socket, b: socket.socket) -> None:
    try:
        while True:
            data = a.recv(BUFFER)
            if not data:
                break
            b.sendall(data)
    except OSError:
        pass
    finally:
        for s in (a, b):
            try:
                s.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass


def handle(client: socket.socket) -> None:
    upstream = None
    try:
        client.settimeout(15)
        head = b""
        while b"\r\n\r\n" not in head and len(head) < 16384:
            chunk = client.recv(4096)
            if not chunk:
                return
            head += chunk

        first_line = head.split(b"\r\n", 1)[0].decode("latin-1")
        parts = first_line.split()
        if len(parts) < 2:
            return

        if parts[0].upper() == "CONNECT":
            host, _, port = parts[1].partition(":")
            port = int(port or 443)
            if any(bad in host for bad in BLOCK):
                # 不给任何响应，直接断开 —— 对客户端就是「连接失败」
                print(f"BLOCK  {host}:{port}", flush=True)
                return
            upstream = socket.create_connection((host, port), timeout=15)
            client.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
            print(f"ALLOW  {host}:{port}", flush=True)
            threading.Thread(target=pipe, args=(client, upstream), daemon=True).start()
            pipe(upstream, client)
        else:
            # 非 CONNECT（明文 HTTP）也要放行，否则会被当成故障
            url = parts[1]
            host = url.split("//", 1)[-1].split("/", 1)[0].partition(":")[0]
            if any(bad in host for bad in BLOCK):
                client.sendall(b"HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n")
                print(f"BLOCK  http {host}", flush=True)
                return
            upstream = socket.create_connection((host, 80), timeout=15)
            upstream.sendall(head)
            print(f"ALLOW  http {host}", flush=True)
            pipe(upstream, client)
    except OSError as exc:
        print(f"ERROR  {exc}", flush=True)
    finally:
        for s in (client, upstream):
            if s is not None:
                try:
                    s.close()
                except OSError:
                    pass


def main() -> None:
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", PORT))
    server.listen(64)
    print(f"listening on 0.0.0.0:{PORT}; blocking {BLOCK}", flush=True)
    while True:
        client, _ = server.accept()
        threading.Thread(target=handle, args=(client,), daemon=True).start()


if __name__ == "__main__":
    main()
