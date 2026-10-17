package estudo.simba.validador.gab;

import estudo.simba.validador.Mode;

/**
 * @param versaoLayout 1 ou 2 (no original vem da escolha do botão na tela do atendimento)
 * @param defeito      reproduz o defeito do original 6.2.6: {@code getCamposArquivo()} devolve null nos carregadores GAB e
 *                     {@code validarCampos} lança NullPointerException ⇒ toda linha com a largura certa vira "Erro desconhecido".
 *                     Padrão: ligado em COMPAT, desligado em ESTRITO.
 */
public record GabConfig(String caso, int versaoLayout, Mode mode, boolean defeito) {
    public static GabConfig of(String caso, int versaoLayout, Mode mode, Boolean defeito) {
        return new GabConfig(caso, versaoLayout, mode, defeito != null ? defeito : mode == Mode.COMPAT);
    }
}
