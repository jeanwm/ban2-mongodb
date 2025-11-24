import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FuncionarioCRUD {
    private MongoCollection<Document> collection;
    private MongoCollection<Document> cargos;
    private Scanner scanner;

    public FuncionarioCRUD(MongoDatabase database, Scanner scanner) {
        this.collection = database.getCollection("funcionarios");
        this.cargos = database.getCollection("cargos");
        this.scanner = scanner;
    }

    public void menu() {
        while (true) {
            System.out.println("\n=== FUNCIONÁRIOS ===");
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
        // Verifica se existem cargos cadastrados
        if (cargos.countDocuments() == 0) {
            System.out.println("Erro: Não existem cargos cadastrados. Cadastre um cargo primeiro!");
            return;
        }

        System.out.print("Nome: ");
        String nome = scanner.nextLine();
        if (nome.trim().isEmpty()) {
            System.out.println("Erro: Nome não pode estar vazio!");
            return;
        }

        // Valida data de nascimento
        Date dataNascimento = null;
        while (dataNascimento == null) {
            System.out.print("Data de nascimento (dd/MM/yyyy): ");
            try {
                dataNascimento = parseDate(scanner.nextLine());
                // Valida se a data de nascimento é razoável (pelo menos 16 anos)
                Date hoje = new Date();
                long diff = hoje.getTime() - dataNascimento.getTime();
                long anos = diff / (1000L * 60 * 60 * 24 * 365);
                if (anos < 16) {
                    System.out.println("Erro: Funcionário deve ter pelo menos 16 anos!");
                    dataNascimento = null;
                }
            } catch (ParseException e) {
                System.out.println("Data inválida! Use o formato dd/MM/yyyy.");
            }
        }

        // Valida data de admissão
        Date dataAdmissao = null;
        while (dataAdmissao == null) {
            System.out.print("Data de admissão (dd/MM/yyyy): ");
            try {
                dataAdmissao = parseDate(scanner.nextLine());
                // Valida se data de admissão não é futura
                if (dataAdmissao.after(new Date())) {
                    System.out.println("Erro: Data de admissão não pode ser futura!");
                    dataAdmissao = null;
                }
            } catch (ParseException e) {
                System.out.println("Data inválida! Use o formato dd/MM/yyyy.");
            }
        }

        // Data de demissão (opcional)
        Date dataDemissao = null;
        System.out.print("Data de demissão (dd/MM/yyyy) [vazio se não houver]: ");
        String dataDemissaoStr = scanner.nextLine();
        if (!dataDemissaoStr.isEmpty()) {
            try {
                dataDemissao = parseDate(dataDemissaoStr);
                if (dataDemissao.before(dataAdmissao)) {
                    System.out.println("Erro: Data de demissão não pode ser anterior à data de admissão!");
                    return;
                }
            } catch (ParseException e) {
                System.out.println("Formato inválido, ignorando data de demissão.");
            }
        }

        // Valida status
        int status = -1;
        while (status == -1) {
            System.out.print("Status (1-Ativo, 0-Inativo): ");
            try {
                status = scanner.nextInt();
                scanner.nextLine();
                if (status != 0 && status != 1) {
                    System.out.println("Erro: Status deve ser 0 ou 1!");
                    status = -1;
                }
            } catch (Exception e) {
                System.out.println("Valor inválido! Digite 0 ou 1.");
                scanner.nextLine();
            }
        }

        // Valida telefone
        String telefone = "";
        while (telefone.isEmpty()) {
            System.out.print("Telefone: ");
            telefone = scanner.nextLine();
            if (!telefone.matches("\\d+")) {
                System.out.println("Erro: Telefone deve conter apenas números!");
                telefone = "";
            }
        }

        // Seleção de cargo com listagem
        String cargoId = selecionarCargo();
        if (cargoId == null) return;

        // Verifica se já existe funcionário com mesmo telefone
        Document funcionarioExistente = collection.find(Filters.eq("telefone", telefone)).first();
        if (funcionarioExistente != null) {
            System.out.println("Erro: Já existe um funcionário cadastrado com este telefone!");
            return;
        }

        Document doc = new Document("nome", nome)
            .append("data_nascimento", dataNascimento)
            .append("data_admissao", dataAdmissao)
            .append("data_demissao", dataDemissao)
            .append("status", status)
            .append("telefone", telefone)
            .append("cargo", new ObjectId(cargoId));

        collection.insertOne(doc);
        System.out.println("Funcionário cadastrado com sucesso!");
    }

    private void listar() {
        long total = collection.countDocuments();
        if (total == 0) {
            System.out.println("Nenhum funcionário cadastrado.");
            return;
        }

        // Busca funcionários com informações do cargo
        AggregateIterable<Document> result = collection.aggregate(List.of(
            new Document("$lookup", new Document()
                .append("from", "cargos")
                .append("localField", "cargo")
                .append("foreignField", "_id")
                .append("as", "cargo_info")),
            new Document("$unwind", "$cargo_info"),
            new Document("$project", new Document()
                .append("nome", 1)
                .append("data_nascimento", 1)
                .append("data_admissao", 1)
                .append("data_demissao", 1)
                .append("status", 1)
                .append("telefone", 1)
                .append("cargo_nome", "$cargo_info.nome"))
        ));

        System.out.println("\n--- FUNCIONÁRIOS CADASTRADOS ---");
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        for (Document doc : result) {
            System.out.printf(
                "Nome: %s | Nascimento: %s | Admissão: %s | Demissão: %s | Status: %s | Telefone: %s | Cargo: %s\n",
                doc.getString("nome"),
                sdf.format(doc.getDate("data_nascimento")),
                sdf.format(doc.getDate("data_admissao")),
                doc.get("data_demissao") != null ? sdf.format(doc.getDate("data_demissao")) : "N/A",
                doc.getInteger("status") == 1 ? "Ativo" : "Inativo",
                doc.getString("telefone"),
                doc.getString("cargo_nome")
            );
        }
        System.out.printf("Total: %d funcionário(s)\n", total);
    }

    private void atualizar() {
        String funcionarioId = selecionarFuncionario("atualizar");
        if (funcionarioId == null) return;

        System.out.print("Novo nome (enter para manter atual): ");
        String nome = scanner.nextLine();

        // Data de nascimento
        Date dataNascimento = null;
        System.out.print("Nova data de nascimento (dd/MM/yyyy) [enter para manter atual]: ");
        String dataNascStr = scanner.nextLine();
        if (!dataNascStr.isEmpty()) {
            while (dataNascimento == null) {
                try {
                    dataNascimento = parseDate(dataNascStr);
                } catch (ParseException e) {
                    System.out.println("Data inválida! Digite novamente ou enter para cancelar: ");
                    dataNascStr = scanner.nextLine();
                    if (dataNascStr.isEmpty()) break;
                }
            }
        }

        // Data de admissão
        Date dataAdmissao = null;
        System.out.print("Nova data de admissão (dd/MM/yyyy) [enter para manter atual]: ");
        String dataAdmStr = scanner.nextLine();
        if (!dataAdmStr.isEmpty()) {
            while (dataAdmissao == null) {
                try {
                    dataAdmissao = parseDate(dataAdmStr);
                } catch (ParseException e) {
                    System.out.println("Data inválida! Digite novamente ou enter para cancelar: ");
                    dataAdmStr = scanner.nextLine();
                    if (dataAdmStr.isEmpty()) break;
                }
            }
        }

        // Data de demissão
        Date dataDemissao = null;
        System.out.print("Nova data de demissão (dd/MM/yyyy) [enter para manter atual, 'null' para remover]: ");
        String dataDemStr = scanner.nextLine();
        if (dataDemStr.equalsIgnoreCase("null")) {
            dataDemissao = null;
        } else if (!dataDemStr.isEmpty()) {
            try {
                dataDemissao = parseDate(dataDemStr);
            } catch (ParseException e) {
                System.out.println("Formato inválido, mantendo data atual.");
            }
        }

        // Status
        Integer status = null;
        System.out.print("Novo status (1-Ativo, 0-Inativo) [enter para manter atual]: ");
        String statusStr = scanner.nextLine();
        if (!statusStr.isEmpty()) {
            try {
                status = Integer.parseInt(statusStr);
                if (status != 0 && status != 1) {
                    System.out.println("Valor inválido, mantendo status atual.");
                    status = null;
                }
            } catch (NumberFormatException e) {
                System.out.println("Valor inválido, mantendo status atual.");
            }
        }

        // Telefone
        System.out.print("Novo telefone [enter para manter atual]: ");
        String telefone = scanner.nextLine();
        if (!telefone.isEmpty() && !telefone.matches("\\d+")) {
            System.out.println("Telefone inválido, mantendo telefone atual.");
            telefone = "";
        }

        // Cargo
        String cargoId = null;
        System.out.print("Deseja alterar o cargo? (s/N): ");
        if (scanner.nextLine().equalsIgnoreCase("s")) {
            cargoId = selecionarCargo();
            if (cargoId == null) {
                System.out.println("Mantendo cargo atual.");
            }
        }

        // Verifica duplicidade de telefone se for alterado
        if (!telefone.isEmpty()) {
            Document funcionarioExistente = collection.find(
                Filters.and(
                    Filters.eq("telefone", telefone),
                    Filters.ne("_id", new ObjectId(funcionarioId))
                )
            ).first();
            if (funcionarioExistente != null) {
                System.out.println("Erro: Já existe outro funcionário com este telefone!");
                return;
            }
        }

        // Monta updates apenas para campos que foram alterados
        List<Document> updates = new ArrayList<>();
        if (!nome.isEmpty()) updates.add(new Document("$set", new Document("nome", nome)));
        if (dataNascimento != null) updates.add(new Document("$set", new Document("data_nascimento", dataNascimento)));
        if (dataAdmissao != null) updates.add(new Document("$set", new Document("data_admissao", dataAdmissao)));
        if (dataDemissao != null || dataDemStr.equalsIgnoreCase("null")) {
            updates.add(new Document("$set", new Document("data_demissao", dataDemissao)));
        }
        if (status != null) updates.add(new Document("$set", new Document("status", status)));
        if (!telefone.isEmpty()) updates.add(new Document("$set", new Document("telefone", telefone)));
        if (cargoId != null) updates.add(new Document("$set", new Document("cargo", new ObjectId(cargoId))));

        if (updates.isEmpty()) {
            System.out.println("Nenhuma alteração realizada.");
            return;
        }

        // Aplica todos os updates
        for (Document update : updates) {
            collection.updateOne(Filters.eq("_id", new ObjectId(funcionarioId)), update);
        }

        System.out.println("Funcionário atualizado com sucesso!");
    }

    private void deletar() {
        String funcionarioId = selecionarFuncionario("deletar");
        if (funcionarioId == null) return;

        // Busca o funcionário para mostrar informações
        Document funcionario = collection.find(Filters.eq("_id", new ObjectId(funcionarioId))).first();
        if (funcionario == null) {
            System.out.println("Funcionário não encontrado!");
            return;
        }

        System.out.printf("Tem certeza que deseja deletar o funcionário '%s'? (s/N): ", 
                         funcionario.getString("nome"));
        String confirmacao = scanner.nextLine();
        
        if (confirmacao.equalsIgnoreCase("s")) {
            collection.deleteOne(Filters.eq("_id", new ObjectId(funcionarioId)));
            System.out.println("Funcionário deletado com sucesso!");
        } else {
            System.out.println("Operação cancelada.");
        }
    }

    /**
     * Método auxiliar para selecionar um funcionário
     */
    private String selecionarFuncionario(String operacao) {
        System.out.print("Digite o nome ou parte do nome para buscar: ");
        String busca = scanner.nextLine();

        // Busca por nomes que contenham o texto digitado
        List<Document> resultados = new ArrayList<>();
        collection.find(Filters.regex("nome", ".*" + busca + ".*", "i"))
                 .into(resultados);

        if (resultados.isEmpty()) {
            System.out.println("Nenhum funcionário encontrado com: " + busca);
            return null;
        }

        if (resultados.size() == 1) {
            Document funcionario = resultados.get(0);
            System.out.printf("Funcionário selecionado: %s (Telefone: %s)\n", 
                            funcionario.getString("nome"), 
                            funcionario.getString("telefone"));
            return funcionario.getObjectId("_id").toHexString();
        }

        // Mostra lista numerada para seleção
        System.out.println("\n--- FUNCIONÁRIOS ENCONTRADOS ---");
        for (int i = 0; i < resultados.size(); i++) {
            Document doc = resultados.get(i);
            System.out.printf("%d. %s | Telefone: %s | Status: %s\n", 
                i + 1, 
                doc.getString("nome"),
                doc.getString("telefone"),
                doc.getInteger("status") == 1 ? "Ativo" : "Inativo");
        }

        System.out.print("Selecione o funcionário para " + operacao + " (1-" + resultados.size() + "): ");
        try {
            int escolha = scanner.nextInt();
            scanner.nextLine();
            
            if (escolha < 1 || escolha > resultados.size()) {
                System.out.println("Seleção inválida!");
                return null;
            }
            
            Document funcionarioSelecionado = resultados.get(escolha - 1);
            System.out.printf("Funcionário selecionado: %s\n", funcionarioSelecionado.getString("nome"));
            return funcionarioSelecionado.getObjectId("_id").toHexString();
            
        } catch (Exception e) {
            System.out.println("Entrada inválida!");
            scanner.nextLine();
            return null;
        }
    }

    /**
     * Método auxiliar para selecionar um cargo
     */
    private String selecionarCargo() {
        List<Document> listaCargos = new ArrayList<>();
        cargos.find().into(listaCargos);

        if (listaCargos.isEmpty()) {
            System.out.println("Nenhum cargo disponível!");
            return null;
        }

        System.out.println("\n--- CARGOS DISPONÍVEIS ---");
        for (int i = 0; i < listaCargos.size(); i++) {
            Document cargo = listaCargos.get(i);
            System.out.printf("%d. %s\n", i + 1, cargo.getString("nome"));
        }

        System.out.print("Selecione o cargo (1-" + listaCargos.size() + "): ");
        try {
            int escolha = scanner.nextInt();
            scanner.nextLine();
            
            if (escolha < 1 || escolha > listaCargos.size()) {
                System.out.println("Seleção inválida!");
                return null;
            }
            
            Document cargoSelecionado = listaCargos.get(escolha - 1);
            System.out.printf("Cargo selecionado: %s\n", cargoSelecionado.getString("nome"));
            return cargoSelecionado.getObjectId("_id").toHexString();
            
        } catch (Exception e) {
            System.out.println("Entrada inválida!");
            scanner.nextLine();
            return null;
        }
    }

    // Helper de data
    private Date parseDate(String dateString) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        sdf.setLenient(false);
        return sdf.parse(dateString);
    }
}