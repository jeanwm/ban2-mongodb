import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

public class ManutencaoCRUD {
    private MongoCollection<Document> collection;
    private Scanner scanner;
    private MongoCollection<Document> equipamentos;
    private MongoCollection<Document> funcionarios;
    
    public ManutencaoCRUD(MongoDatabase database, Scanner scanner) {
        this.collection = database.getCollection("manutencoes");
        this.equipamentos = database.getCollection("equipamentos");
        this.funcionarios = database.getCollection("funcionarios");
        this.scanner = scanner;
    }
    
    public void menu() {
        while (true) {
            System.out.println("\n=== MANUTENÇÕES ===");
            System.out.println("1. Cadastrar");
            System.out.println("2. Listar");
            System.out.println("3. Atualizar");
            System.out.println("4. Deletar");
            System.out.println("5. Voltar");
            System.out.print("Escolha: ");

            int opcao = scanner.nextInt();
            scanner.nextLine();

            switch (opcao) {
                case 1 -> cadastrar();
                case 2 -> listar();
                case 3 -> atualizar();
                case 4 -> deletar();
                case 5 -> { return; }
                default -> System.out.println("Opção inválida!");
            }
        }
    }

    private void cadastrar() {
        // Verificar se existem equipamentos cadastrados
        if (equipamentos.countDocuments() == 0) {
            System.out.println("Erro: Não existem equipamentos cadastrados!");
            return;
        }

        // Verificar se existem funcionários cadastrados
        if (funcionarios.countDocuments() == 0) {
            System.out.println("Erro: Não existem funcionários cadastrados!");
            return;
        }

        System.out.print("Custo: ");
        float custo;
        try {
            custo = scanner.nextFloat();
            scanner.nextLine();
            if (custo < 0) {
                System.out.println("Erro: O custo não pode ser negativo!");
                return;
            }
        } catch (Exception e) {
            System.out.println("Erro: Custo inválido!");
            scanner.nextLine();
            return;
        }

        System.out.print("Status (0=Pendente, 1=Em Andamento, 2=Concluída): ");
        int status;
        try {
            status = scanner.nextInt();
            scanner.nextLine();
            if (status < 0 || status > 2) {
                System.out.println("Erro: Status deve ser 0, 1 ou 2!");
                return;
            }
        } catch (Exception e) {
            System.out.println("Erro: Status inválido!");
            scanner.nextLine();
            return;
        }

        System.out.print("Data prevista (dd/MM/yyyy): ");
        LocalDate dataPrevista = parseDate(scanner.nextLine());
        if (dataPrevista == null) {
            System.out.println("Erro: Data prevista inválida!");
            return;
        }

        System.out.print("Data realizada (dd/MM/yyyy) [vazio = null]: ");
        String dataRealizadaStr = scanner.nextLine();
        LocalDate dataRealizada = null;
        if (!dataRealizadaStr.isBlank()) {
            dataRealizada = parseDate(dataRealizadaStr);
            if (dataRealizada == null) {
                System.out.println("Erro: Data realizada inválida!");
                return;
            }
        }

        // Selecionar equipamento
        ObjectId idEquipamento = selecionarEquipamento();
        if (idEquipamento == null) return;

        // Selecionar funcionário
        ObjectId idFuncionario = selecionarFuncionario();
        if (idFuncionario == null) return;

        Document doc = new Document()
                .append("custo", custo)
                .append("status", status)
                .append("data_prevista", toDate(dataPrevista))
                .append("data_realizada", dataRealizada != null ? toDate(dataRealizada) : null)
                .append("id_equipamento", idEquipamento)
                .append("id_funcionario", idFuncionario);

        collection.insertOne(doc);
        System.out.println("Manutenção cadastrada com sucesso!");
    }

    private void listar() {
        long total = collection.countDocuments();
        if (total == 0) {
            System.out.println("Nenhuma manutenção cadastrada.");
            return;
        }

        System.out.println("\n--- MANUTENÇÕES CADASTRADAS ---");
        
        // Buscar manutenções com informações dos relacionamentos
        AggregateIterable<Document> results = collection.aggregate(List.of(
            new Document("$lookup", new Document()
                .append("from", "equipamentos")
                .append("localField", "id_equipamento")
                .append("foreignField", "_id")
                .append("as", "equipamento")),
            new Document("$lookup", new Document()
                .append("from", "funcionarios")
                .append("localField", "id_funcionario")
                .append("foreignField", "_id")
                .append("as", "funcionario")),
            new Document("$unwind", "$equipamento"),
            new Document("$unwind", "$funcionario")
        ));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        
        for (Document doc : results) {
            String statusStr = switch(doc.getInteger("status")) {
                case 0 -> "Pendente";
                case 1 -> "Em Andamento";
                case 2 -> "Concluída";
                default -> "Desconhecido";
            };

            Date dataPrevista = doc.getDate("data_prevista");
            Date dataRealizada = doc.getDate("data_realizada");
            
            String dataPrevistaStr = dataPrevista != null ? 
                dataPrevista.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(formatter) : "N/A";
            String dataRealizadaStr = dataRealizada != null ? 
                dataRealizada.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(formatter) : "N/A";

            System.out.printf("ID: %s | Custo: R$%.2f | Status: %s\n",
                doc.getObjectId("_id").toHexString(),
                doc.getDouble("custo"),
                statusStr
            );
            System.out.printf("  Prevista: %s | Realizada: %s\n",
                dataPrevistaStr,
                dataRealizadaStr
            );
            System.out.printf("  Equipamento: %s | Funcionário: %s\n\n",
                doc.get("equipamento", Document.class).getString("modelo"),
                doc.get("funcionario", Document.class).getString("nome")
            );
        }
        System.out.printf("Total: %d manutenção(ões)\n", total);
    }

    private void atualizar() {
        Document manutencao = selecionarManutencao("atualizar");
        if (manutencao == null) return;

        ObjectId id = manutencao.getObjectId("_id");

        System.out.print("Novo custo [" + manutencao.getDouble("custo") + "]: ");
        String custoInput = scanner.nextLine();
        float custo = custoInput.isBlank() ? manutencao.getDouble("custo").floatValue() : Float.parseFloat(custoInput);
        
        if (custo < 0) {
            System.out.println("Erro: O custo não pode ser negativo!");
            return;
        }

        System.out.print("Novo status (0=Pendente, 1=Em Andamento, 2=Concluída) [" + manutencao.getInteger("status") + "]: ");
        String statusInput = scanner.nextLine();
        int status = statusInput.isBlank() ? manutencao.getInteger("status") : Integer.parseInt(statusInput);
        
        if (status < 0 || status > 2) {
            System.out.println("Erro: Status deve ser 0, 1 ou 2!");
            return;
        }

        Date dataPrevistaAtual = manutencao.getDate("data_prevista");
        String dataPrevistaStrAtual = dataPrevistaAtual != null ? 
            dataPrevistaAtual.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "N/A";
        
        System.out.print("Nova data prevista (dd/MM/yyyy) [" + dataPrevistaStrAtual + "]: ");
        String dataPrevistaInput = scanner.nextLine();
        LocalDate dataPrevista = dataPrevistaInput.isBlank() ? 
            dataPrevistaAtual.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : parseDate(dataPrevistaInput);
        
        if (dataPrevista == null && !dataPrevistaInput.isBlank()) {
            System.out.println("Erro: Data prevista inválida!");
            return;
        }

        Date dataRealizadaAtual = manutencao.getDate("data_realizada");
        String dataRealizadaStrAtual = dataRealizadaAtual != null ? 
            dataRealizadaAtual.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "N/A";
        
        System.out.print("Nova data realizada (dd/MM/yyyy) [" + dataRealizadaStrAtual + "]: ");
        String dataRealizadaInput = scanner.nextLine();
        LocalDate dataRealizada = null;
        if (!dataRealizadaInput.isBlank()) {
            dataRealizada = parseDate(dataRealizadaInput);
            if (dataRealizada == null) {
                System.out.println("Erro: Data realizada inválida!");
                return;
            }
        }

        // Atualizar documento
        collection.updateOne(
            Filters.eq("_id", id),
            Updates.combine(
                Updates.set("custo", custo),
                Updates.set("status", status),
                Updates.set("data_prevista", toDate(dataPrevista)),
                Updates.set("data_realizada", dataRealizada != null ? toDate(dataRealizada) : null)
            )
        );

        System.out.println("Manutenção atualizada com sucesso!");
    }

    private void deletar() {
        Document manutencao = selecionarManutencao("deletar");
        if (manutencao == null) return;

        // Confirmação antes de deletar
        System.out.print("Tem certeza que deseja deletar esta manutenção? (s/N): ");
        String confirmacao = scanner.nextLine();
        
        if (confirmacao.equalsIgnoreCase("s")) {
            collection.deleteOne(Filters.eq("_id", manutencao.getObjectId("_id")));
            System.out.println("Manutenção deletada com sucesso!");
        } else {
            System.out.println("Operação cancelada.");
        }
    }

    /**
     * Método auxiliar para selecionar uma manutenção
     */
    private Document selecionarManutencao(String operacao) {
        long total = collection.countDocuments();
        if (total == 0) {
            System.out.println("Nenhuma manutenção cadastrada.");
            return null;
        }

        System.out.print("Digite parte do ID ou 'listar' para ver todas: ");
        String busca = scanner.nextLine();

        List<Document> resultados = new ArrayList<>();
        
        if (busca.equalsIgnoreCase("listar") || busca.isBlank()) {
            // Listar todas as manutenções
            collection.find().into(resultados);
        } else {
            // Buscar por ID parcial
            collection.find(Filters.regex("_id", busca)).into(resultados);
        }

        if (resultados.isEmpty()) {
            System.out.println("Nenhuma manutenção encontrada.");
            return null;
        }

        if (resultados.size() == 1) {
            return resultados.get(0);
        }

        // Mostrar lista numerada para seleção
        System.out.println("\n--- MANUTENÇÕES ENCONTRADAS ---");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        
        for (int i = 0; i < resultados.size(); i++) {
            Document doc = resultados.get(i);
            String statusStr = switch(doc.getInteger("status")) {
                case 0 -> "Pendente";
                case 1 -> "Em Andamento";
                case 2 -> "Concluída";
                default -> "Desconhecido";
            };
            
            Date dataPrevista = doc.getDate("data_prevista");
            String dataPrevistaStr = dataPrevista != null ? 
                dataPrevista.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().format(formatter) : "N/A";

            System.out.printf("%d. ID: %s | Custo: R$%.2f | Status: %s | Data: %s\n",
                i + 1,
                doc.getObjectId("_id").toHexString(),
                doc.getDouble("custo"),
                statusStr,
                dataPrevistaStr
            );
        }

        System.out.print("Selecione a manutenção para " + operacao + " (1-" + resultados.size() + "): ");
        try {
            int escolha = scanner.nextInt();
            scanner.nextLine();
            
            if (escolha < 1 || escolha > resultados.size()) {
                System.out.println("Seleção inválida!");
                return null;
            }
            
            return resultados.get(escolha - 1);
            
        } catch (Exception e) {
            System.out.println("Entrada inválida!");
            scanner.nextLine();
            return null;
        }
    }

    /**
     * Método auxiliar para selecionar equipamento
     */
    private ObjectId selecionarEquipamento() {
        List<Document> equipamentosList = new ArrayList<>();
        equipamentos.find().into(equipamentosList);

        if (equipamentosList.isEmpty()) {
            System.out.println("Nenhum equipamento disponível.");
            return null;
        }

        System.out.println("\n--- EQUIPAMENTOS DISPONÍVEIS ---");
        for (int i = 0; i < equipamentosList.size(); i++) {
            Document equip = equipamentosList.get(i);
            System.out.printf("%d. %s - %s\n",
                i + 1,
                equip.getString("modelo"),
                equip.getString("marca")
            );
        }

        System.out.print("Selecione o equipamento (1-" + equipamentosList.size() + "): ");
        try {
            int escolha = scanner.nextInt();
            scanner.nextLine();
            
            if (escolha < 1 || escolha > equipamentosList.size()) {
                System.out.println("Seleção inválida!");
                return null;
            }
            
            return equipamentosList.get(escolha - 1).getObjectId("_id");
            
        } catch (Exception e) {
            System.out.println("Entrada inválida!");
            scanner.nextLine();
            return null;
        }
    }

    /**
     * Método auxiliar para selecionar funcionário
     */
    private ObjectId selecionarFuncionario() {
        List<Document> funcionariosList = new ArrayList<>();
        funcionarios.find().into(funcionariosList);

        if (funcionariosList.isEmpty()) {
            System.out.println("Nenhum funcionário disponível.");
            return null;
        }

        System.out.println("\n--- FUNCIONÁRIOS DISPONÍVEIS ---");
        for (int i = 0; i < funcionariosList.size(); i++) {
            Document func = funcionariosList.get(i);
            System.out.printf("%d. %s - %s\n",
                i + 1,
                func.getString("nome"),
                func.getString("email")
            );
        }

        System.out.print("Selecione o funcionário (1-" + funcionariosList.size() + "): ");
        try {
            int escolha = scanner.nextInt();
            scanner.nextLine();
            
            if (escolha < 1 || escolha > funcionariosList.size()) {
                System.out.println("Seleção inválida!");
                return null;
            }
            
            return funcionariosList.get(escolha - 1).getObjectId("_id");
            
        } catch (Exception e) {
            System.out.println("Entrada inválida!");
            scanner.nextLine();
            return null;
        }
    }

    // Helpers
    private LocalDate parseDate(String dateString) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return LocalDate.parse(dateString, formatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Date toDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}