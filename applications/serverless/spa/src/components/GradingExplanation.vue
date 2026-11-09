<script setup lang="ts">
import type { GradingExplanation } from '@/api/types';

/**
 * "Como as notas são calculadas", for every user. Written in Portuguese for the students (exception to the English
 * interface); every number comes from grading.properties / milestones.properties (openspec capability milestone-grading).
 */
defineProps<{ gx: GradingExplanation }>();
</script>

<template>
  <section class="card mb-5" id="grading-explanation" lang="pt-BR" aria-labelledby="grading-explanation-title">
    <div class="card-body">
      <h5 class="card-title" id="grading-explanation-title">Como as notas são calculadas</h5>
      <p class="mb-0">Esta explicação vale para todos os grupos. Os números abaixo são os que estão em uso no sistema.</p>

      <h6>Marcos</h6>
      <ul id="grading-explanation-milestones">
        <li v-for="m in gx.milestones" :key="m.label">{{ m.label }}: de {{ m.start }} a {{ m.end }}</li>
      </ul>
      <p>Cada marco vale de 0 a {{ gx.maxPoints }} pontos, e o total vai de 0 a {{ gx.totalPoints }}
        (coluna &ldquo;Total&rdquo; no período &ldquo;All&rdquo;). Enquanto um marco não termina, a nota dele é provisória
        e aparece com &ldquo;(in progress)&rdquo;: ela ainda pode mudar.</p>

      <h6>Quem recebe nota e quais commits contam</h6>
      <ul>
        <li>Recebem nota os membros do grupo, cadastrados pelo professor.</li>
        <li>Um commit conta para você quando o e-mail configurado no git é um e-mail da sua conta do GitHub. Commits feitos
          com outro e-mail aparecem na tabela como &ldquo;No GitHub account&rdquo; e não contam para ninguém, a menos que
          o professor associe essa identidade a você.</li>
        <li>Commits de merge não contam. Contas de bots<template v-if="gx.excludesLogins"> e a conta do professor</template> não recebem nota.</li>
      </ul>

      <h6>Os três fatores</h6>
      <ul id="grading-explanation-factors">
        <li><strong>Linhas efetivas (peso {{ gx.weightEffectiveLines }}):</strong> adições + remoções de cada
          commit, contando no máximo {{ gx.lineCap }} linhas por commit. Commits que apenas movem ou renomeiam
          muitos arquivos ({{ gx.reorganizationMinFiles }} arquivos ou mais, com adições e remoções
          equilibradas, a menor sendo pelo menos {{ gx.reorganizationBalance }} da maior) contam só
          {{ gx.reorganizationFactor }} das linhas.</li>
        <li><strong>Dias ativos (peso {{ gx.weightActiveDays }}):</strong> quantidade de dias diferentes em que
          você fez commits no marco ({{ gx.timeZone }}).</li>
        <li><strong>Commits relevantes (peso {{ gx.weightRelevantCommits }}):</strong> commits com pelo menos
          {{ gx.relevantCommitMinLines }} linhas alteradas.</li>
      </ul>

      <h6>Da participação à nota</h6>
      <ul>
        <li>Em cada fator, a sua participação é a sua parte no total dos alunos do grupo. A soma dessas participações,
          com os pesos acima, é a sua participação no índice.</li>
        <li>A parte justa é 1 dividido pelo número de alunos do grupo. Quem atinge pelo menos
          {{ gx.tolerance }} da parte justa recebe a nota máxima do marco ({{ gx.maxPoints }}).
          Abaixo disso, a nota é proporcional.</li>
        <li id="grading-explanation-example">Exemplo: num grupo de {{ gx.exampleStudents }} alunos, a parte justa é
          {{ gx.exampleFairShare }}. Quem tem {{ gx.exampleFullShare }} ou mais do índice recebe
          {{ gx.exampleFullGrade }}; quem tem {{ gx.exampleLowShare }} recebe
          {{ gx.exampleLowGrade }}.</li>
        <li>Na tabela, passe o mouse sobre a sua nota para ver os seus números: linhas efetivas, dias ativos, commits
          relevantes e participação.</li>
      </ul>

      <h6>Dicas</h6>
      <ul class="mb-0">
        <li>Faça commits com frequência, em dias diferentes, e não tudo de uma vez no fim do marco.</li>
        <li>Prefira commits pequenos e com sentido: um commit gigante conta no máximo {{ gx.lineCap }} linhas.</li>
        <li>Configure o git com o e-mail da sua conta do GitHub:
          <code>git config --global user.email "seu-email@exemplo.com"</code></li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
#grading-explanation h6 { margin-top: 1rem; }
#grading-explanation ul { margin-bottom: 0.5rem; }
</style>
