import uvicorn
from mcp.server.fastmcp import FastMCP
from starlette.applications import Starlette
from starlette.routing import Mount

mcp = FastMCP("py-mcp-server"
# , dependencies=[" "," "],
)

@mcp.tool(description="mcpai 단어를 해석합니다.")
def mcpAiWord(word: str) -> str:
    """단어의 첫 글자의 유니코드를 단어 길이만큼 곱해 변환합니다."""
    if not word:
        return "m.ai"

    convert = str(ord(word[0]) * len(word))

    return f"m{convert}/py.ai"

app = Starlette(
    routes=[
        Mount('/', app=mcp.sse_app()),
    ]
)

if __name__ == "__main__":
    host = "127.0.0.1"
    port = 8088

    print(f"MCP 서버를 {host}:{port} 에서 SSE 모드로 실행합니다.")
    uvicorn.run(app, host=host, port=port)

