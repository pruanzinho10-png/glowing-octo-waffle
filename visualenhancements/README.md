# Visual Enhancements (Minecraft 1.21.11, Fabric)

Mod 100% client-side com três módulos visuais e uma interface organizada nas
categorias **Todos**, **Ambiente** e **Combate**. O menu inclui transições,
realce animado dos módulos ativos e controles de configuração (tecla padrão:
**Shift direito**, configurável em Controles).

## Módulos

- **Chuva na tela** — gotículas frias e translúcidas caindo sobre a tela.
- **Chão molhado** — reflexo branco-azulado nos blocos expostos à chuva.
- **Aura de impacto** — ao acertar um mob ou jogador (acerto confirmado pelo
  dano), uma aura energética envolve o corpo do alvo e o acompanha. A cor
  (Amarelo, Roxo ou Rosa) é escolhida no menu.

As escolhas ficam em `config/visualenhancements.json`.

## Baixar o .jar pelo GitHub

1. Envie o projeto para um repositório seu.
2. Abra a aba **Actions** → clique na execução mais recente de **build**.
3. Em **Artifacts**, baixe **visualenhancements-jar** (extraia o .zip uma vez).
4. Coloque o `.jar` em `.minecraft/mods/` junto com o **Fabric API**.

Para uma página de download fixa, crie uma tag que comece com `v`
(`git tag v1.1.0 && git push origin v1.1.0`); o `.jar` aparece em **Releases**.

## Requisitos

- Minecraft 1.21.11, Fabric Loader 0.18.1+, Fabric API para 1.21.11, Java 21.

## Licenças

O código do mod é MIT. A tipografia do menu usa a fonte **Inter**
(SIL Open Font License 1.1); o aviso de licença está em `licenses/Inter-OFL.txt`.
