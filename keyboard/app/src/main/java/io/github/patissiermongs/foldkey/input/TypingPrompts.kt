package io.github.patissiermongs.foldkey.input

import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.hangul.Dubeolsik
import io.github.patissiermongs.foldkey.hangul.Jamo

object TypingPrompts {
    val code: List<String> = listOf(
        "git status",
        "ls -la /var/log",
        "grep -rn todo src/",
        "x = [1, 2, 3]",
        "kubectl get pods -n kube-system",
        "vim .vimrc",
        "git log -3 --oneline",
        "a[i] = b[j]; i = i - 1",
        "tail -f /var/log/syslog",
        "go test ./...",
        "git commit -m 'fix typo'",
        "chmod 755 run.sh",
        "python3 -m venv .venv",
        "sed -n 1,20p notes.txt",
        "import os, sys, json",
        "docker ps -a",
        "rm -rf build/ dist/",
        "path = '/tmp/cache'",
        "ssh -p 2222 dev.example.com",
        "type dir\\sub\\file.txt",
        "make -j8 test",
        "echo `date`",
        "curl -s localhost/api/v1",
        "set -euo pipefail",
    )

    val korean: List<String> = listOf(
        "오늘 저녁에 같이 밥 먹을래요",
        "회의 자료는 메일로 보내 드릴게요.",
        "주말에 날이 좋으면 산에 가요.",
        "지금 출발하면 열 시 전에 도착해요.",
        "고마워요, 내일 다시 연락할게요.",
        "커피 한 잔 하실래요",
        "문서를 확인하고 답장 주세요.",
        "버스가 늦어서 조금 늦을 것 같아요.",
        "택배는 문 앞에 두고 가 주세요.",
        "다음 주 화요일 오후에 만나요.",
        "비가 와서 우산을 챙겨 나가요.",
        "저는 지금 회사로 가는 길이에요.",
    )

    val english: List<String> = listOf(
        "see you at the station at nine.",
        "the quick brown fox jumps over the lazy dog.",
        "please send me the file, thanks.",
        "i will call you back in an hour.",
        "the meeting moved to friday afternoon.",
        "can you check the numbers again.",
        "lunch is on me today, my friend.",
        "the train leaves at seven, do not be late.",
    )

    private const val ASCII = "abcdefghijklmnopqrstuvwxyz0123456789`-=\\[];',./"
    private const val PROSE = "abcdefghijklmnopqrstuvwxyz0123456789,."

    fun keys(text: String): List<Char>? {
        val out = ArrayList<Char>()
        for (c in text) {
            when {
                c == ' ' || c in ASCII -> out.add(c)
                else -> {
                    val jamo = Jamo.keyJamo(c) ?: return null
                    for (j in jamo) out.add(Dubeolsik.key(j) ?: return null)
                }
            }
        }
        return out
    }

    fun typeable(text: String, layer: Layer): Boolean {
        val allowed = if (layer == Layer.GENERAL) PROSE else ASCII
        return keys(text)?.all { it == ' ' || it in allowed } == true
    }

    fun pool(layer: Layer): List<String> =
        if (layer == Layer.GENERAL) korean.indices.flatMap { listOfNotNull(korean[it], english.getOrNull(it)) } else code

    fun round(layer: Layer, round: Int, keys: Int): List<String> {
        val pool = pool(layer)
        val out = ArrayList<String>()
        var count = 0
        var i = round * ROUND_STEP
        while (count < keys) {
            val line = pool[i % pool.size]
            out.add(line)
            count += keys(line)?.count { it != ' ' } ?: 0
            i++
        }
        return out
    }

    private const val ROUND_STEP = 5
}
