import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Scanner;

public class ClienteCRUD {
    private MongoCollection<Document> clientes;
    private MongoCollection<Document> telefones;
    private MongoCollection<Document> planos;
    private Scanner scanner;
    
    public ClienteCRUD(MongoCollection<Document> clientes,
                       MongoCollection<Document> telefones,
                       MongoCollection<Document> planos,
                       Scanner scanner) {
        this.clientes  = clientes;
        this.telefones = telefones;
        this.planos    = planos;
        this.scanner   = scanner;
    }
    
    public void menu() {
        while (true) {
            System.out.println("\n=== CLIENTES ===");
            System.out.println("1. Cadastrar");
            System.out.println("2. Listar");
            System.out.println("3. Atualizar");
            System.out.println("4. Deletar");
            System.out.println("5. Vincular Plano");
            System.out.println("6. Voltar");
            System.out.print("Escolha: ");
            
            int opcao = scanner.nextInt();
            scanner.nextLine();
            
            switch (opcao) {
                case 1 -> cadastrar();
                case 2 -> listar();
                case 3 -> atualizar();
                case 4 -> deletar();
                case 5 -> vincular();
                case 6 -> { return; }
                default -> System.out.println("Opção inválida!");
            }
        }
    }
    
    private void cadastrar() {
        System.out.print("Nome: ");
        String nome = scanner.nextLine().trim();
        
        if (nome.isEmpty()) {
            System.out.println("Erro: Nome não pode estar vazio!");
            return;
        }

        Date dataNascimento = inputDate("Data de nascimento (dd/mm/aaaa): ");
        if (dataNascimento == null) return;

        Date dataAdesao = inputDate("Data de adesão (dd/mm/aaaa): ");
        if (dataAdesao == null) return;

        // validacao de status
        int status = -1;
        while (status != 0 && status != 1) {
            System.out.print("Status (0-Inativo, 1-Ativo): ");
            try {
                status = scanner.nextInt();
                scanner.nextLine();
                if (status != 0 && status != 1) {
                    System.out.println("Erro: Status deve ser 0 (Inativo) ou 1 (Ativo)!");
                }
            } catch (Exception e) {
                System.out.println("Erro: Digite um número válido!");
                scanner.nextLine();
            }
        }
        
        System.out.print("Telefone: ");
        String telefone = scanner.nextLine().trim();
        
        if (telefone.isEmpty()) {
            System.out.println("Erro: Telefone não pode estar vazio!");
            return;
        }

        // verifica se o telefone ja existe
        Document telefoneExistente = telefones.find(Filters.eq("numero", telefone)).first();
        ObjectId idTelefone;
        
        if (telefoneExistente != null) {
            System.out.println("Aviso: Telefone já cadastrado! Usando telefone existente.");
            idTelefone = telefoneExistente.getObjectId("_id");
        } else {
            Document docTelefone = new Document("numero", telefone);
            telefones.insertOne(docTelefone);
            idTelefone = docTelefone.getObjectId("_id");
        }

        Document cliente = new Document("nome", nome)
                .append("data_nascimento", dataNascimento)
                .append("data_adesao", dataAdesao)
                .append("status", status)
                .append("id_telefone", idTelefone)
                .append("id_plano", null);

        clientes.insertOne(cliente);
        System.out.println("Cliente cadastrado com sucesso!");
    }
    
    private void listar() {
        long total = clientes.countDocuments();
        if (total == 0) {
            System.out.println("Nenhum cliente cadastrado.");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        
        System.out.println("\n--- CLIENTES CADASTRADOS ---");
        MongoCursor<Document> cursor = clientes.find().iterator();
        
        while (cursor.hasNext()) {
            Document c = cursor.next();
            ObjectId id = c.getObjectId("_id");
            
            Document telefone = null;
            if (c.get("id_telefone") != null) {
                telefone = telefones.find(new Document("_id", c.getObjectId("id_telefone"))).first();
            }

            Document plano = null;
            if (c.get("id_plano") != null) {
                plano = planos.find(new Document("_id", c.getObjectId("id_plano"))).first();
            }

            System.out.printf(
                "ID: %s | Nome: %s | Nascimento: %s | Adesão: %s | Status: %s | Telefone: %s | Plano: %s\n",
                id.toString(),
                c.getString("nome"),
                sdf.format(c.getDate("data_nascimento")),
                sdf.format(c.getDate("data_adesao")),
                c.getInteger("status") == 1 ? "Ativo" : "Inativo",
                telefone != null ? telefone.getString("numero") : "Não cadastrado",
                plano != null ? plano.getString("nome") : "Nenhum"
            );
        }
        System.out.printf("Total: %d cliente(s)\n", total);
    }
    
    private void atualizar() {
        Document cliente = selecionarCliente("atualizar");
        if (cliente == null) return;

        ObjectId id = cliente.getObjectId("_id");

        System.out.print("Novo nome (Enter para manter '" + cliente.getString("nome") + "'): ");
        String nome = scanner.nextLine().trim();
        if (nome.isEmpty()) {
            nome = cliente.getString("nome");
        }

        System.out.print("Nova data de nascimento (dd/mm/aaaa ou Enter para manter): ");
        Date dataNascimento = inputDateOptional(cliente.getDate("data_nascimento"));

        System.out.print("Nova data de adesão (dd/mm/aaaa ou Enter para manter): ");
        Date dataAdesao = inputDateOptional(cliente.getDate("data_adesao"));

        // validacao de status
        int status = -1;
        while (status != 0 && status != 1) {
            System.out.print("Novo status (0-Inativo, 1-Ativo, Enter para manter): ");
            String statusInput = scanner.nextLine().trim();
            if (statusInput.isEmpty()) {
                status = cliente.getInteger("status");
                break;
            }
            try {
                status = Integer.parseInt(statusInput);
                if (status != 0 && status != 1) {
                    System.out.println("Erro: Status deve ser 0 (Inativo) ou 1 (Ativo)!");
                }
            } catch (Exception e) {
                System.out.println("Erro: Digite um número válido!");
            }
        }

        System.out.print("Novo telefone (Enter para manter): ");
        String telefone = scanner.nextLine().trim();
        ObjectId idTelefone = cliente.getObjectId("id_telefone");
        
        if (!telefone.isEmpty()) {
            Document telefoneExistente = telefones.find(Filters.eq("numero", telefone)).first();
            if (telefoneExistente != null) {
                idTelefone = telefoneExistente.getObjectId("_id");
            } else {
                telefones.updateOne(
                    Filters.eq("_id", idTelefone),
                    Updates.set("numero", telefone)
                );
            }
        }

        Document update = new Document("$set",
            new Document("nome", nome)
                .append("data_nascimento", dataNascimento)
                .append("data_adesao", dataAdesao)
                .append("status", status)
                .append("id_telefone", idTelefone)
        );

        UpdateResult result = clientes.updateOne(new Document("_id", id), update);

        if (result.getModifiedCount() > 0) {
            System.out.println("Cliente atualizado com sucesso!");
        } else {
            System.out.println("Nenhuma alteração realizada.");
        }
    }
    
    private void deletar() {
        Document cliente = selecionarCliente("deletar");
        if (cliente == null) return;

        ObjectId id = cliente.getObjectId("_id");

        System.out.print("Tem certeza que deseja deletar o cliente '" + 
                        cliente.getString("nome") + "'? (s/N): ");
        String confirmacao = scanner.nextLine();
        
        if (confirmacao.equalsIgnoreCase("s")) {
            ObjectId idTelefone = cliente.getObjectId("id_telefone");
            telefones.deleteOne(Filters.eq("_id", idTelefone));
            
            DeleteResult result = clientes.deleteOne(new Document("_id", id));
            
            if (result.getDeletedCount() > 0) {
                System.out.println("Cliente deletado com sucesso!");
            } else {
                System.out.println("Erro ao deletar cliente!");
            }
        } else {
            System.out.println("Operação cancelada.");
        }
    }
    
    private void vincular() {
        Document cliente = selecionarCliente("vincular plano");
        if (cliente == null) return;

        Document plano = selecionarPlano();
        if (plano == null) return;

        ObjectId idCliente = cliente.getObjectId("_id");
        ObjectId idPlano = plano.getObjectId("_id");

        UpdateResult result = clientes.updateOne(
            new Document("_id", idCliente),
            new Document("$set", new Document("id_plano", idPlano))
        );

        if (result.getModifiedCount() > 0) {
            System.out.println("Cliente '" + cliente.getString("nome") + 
                             "' vinculado ao plano '" + plano.getString("nome") + "' com sucesso!");
        } else {
            System.out.println("Nenhuma alteração realizada.");
        }
    }

    /**
     * metodo auxiliar para selecionar um cliente pelo nome
     */
    private Document selecionarCliente(String operacao) {
        System.out.print("Digite o nome ou parte do nome do cliente para buscar: ");
        String busca = scanner.nextLine().trim();

        if (busca.isEmpty()) {
            System.out.println("Busca não pode estar vazia!");
            return null;
        }

        // busca por nomes que contenham o texto digitado (case insensitive)
        List<Document> resultados = new ArrayList<>();
        clientes.find(Filters.regex("nome", ".*" + busca + ".*", "i"))
                 .into(resultados);

        if (resultados.isEmpty()) {
            System.out.println("Nenhum cliente encontrado com: '" + busca + "'");
            return null;
        }

        if (resultados.size() == 1) {
            // Se encontrou apenas um, usa automaticamente
            Document cliente = resultados.get(0);
            System.out.println("Cliente selecionado: " + cliente.getString("nome"));
            return cliente;
        }

        // Se encontrou múltiplos, mostra lista para seleção
        System.out.println("\n--- CLIENTES ENCONTRADOS ---");
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        for (int i = 0; i < resultados.size(); i++) {
            Document doc = resultados.get(i);
            System.out.printf("%d. Nome: %s | Telefone: %s | Status: %s\n", 
                i + 1, 
                doc.getString("nome"),
                getTelefoneCliente(doc),
                doc.getInteger("status") == 1 ? "Ativo" : "Inativo");
        }

        System.out.print("Selecione o cliente para " + operacao + " (1-" + resultados.size() + "): ");
        try {
            int escolha = scanner.nextInt();
            scanner.nextLine();
            
            if (escolha < 1 || escolha > resultados.size()) {
                System.out.println("Seleção inválida!");
                return null;
            }
            
            Document clienteSelecionado = resultados.get(escolha - 1);
            System.out.println("Cliente selecionado: " + clienteSelecionado.getString("nome"));
            return clienteSelecionado;
            
        } catch (Exception e) {
            System.out.println("Entrada inválida!");
            scanner.nextLine();
            return null;
        }
    }

    /**
     * metodo auxiliar para selecionar um plano
     */
    private Document selecionarPlano() {
        long totalPlanos = planos.countDocuments();
        if (totalPlanos == 0) {
            System.out.println("Nenhum plano cadastrado!");
            return null;
        }

        System.out.print("Digite o nome ou parte do nome do plano para buscar: ");
        String busca = scanner.nextLine().trim();

        List<Document> resultados;
        if (busca.isEmpty()) {
            // se busca vazia, mostra todos os planos
            resultados = new ArrayList<>();
            planos.find().into(resultados);
        } else {
            // busca por nomes que contenham o texto digitado (case insensitive)
            resultados = new ArrayList<>();
            planos.find(Filters.regex("nome", ".*" + busca + ".*", "i"))
                   .into(resultados);
        }

        if (resultados.isEmpty()) {
            System.out.println("Nenhum plano encontrado com: '" + busca + "'");
            return null;
        }

        if (resultados.size() == 1) {
            // se encontrou apenas um, usa automaticamente
            Document plano = resultados.get(0);
            System.out.println("Plano selecionado: " + plano.getString("nome"));
            return plano;
        }

        // se encontrou multiplos, mostra lista para selecao
        System.out.println("\n--- PLANOS ENCONTRADOS ---");
        for (int i = 0; i < resultados.size(); i++) {
            Document doc = resultados.get(i);
            System.out.printf("%d. Nome: %s | Valor: R$%.2f\n", 
                i + 1, 
                doc.getString("nome"),
                doc.getDouble("valor"));
        }

        System.out.print("Selecione o plano (1-" + resultados.size() + "): ");
        try {
            int escolha = scanner.nextInt();
            scanner.nextLine();
            
            if (escolha < 1 || escolha > resultados.size()) {
                System.out.println("Seleção inválida!");
                return null;
            }
            
            Document planoSelecionado = resultados.get(escolha - 1);
            System.out.println("Plano selecionado: " + planoSelecionado.getString("nome"));
            return planoSelecionado;
            
        } catch (Exception e) {
            System.out.println("Entrada inválida!");
            scanner.nextLine();
            return null;
        }
    }

    /**
     * obtem o telefone do cliente
     */
    private String getTelefoneCliente(Document cliente) {
        if (cliente.get("id_telefone") == null) {
            return "Não cadastrado";
        }
        Document telefone = telefones.find(
            new Document("_id", cliente.getObjectId("id_telefone"))
        ).first();
        return telefone != null ? telefone.getString("numero") : "Não encontrado";
    }

    // conversao de data para Date (obrigatorio)
    private Date inputDate(String msg) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        sdf.setLenient(false);

        while (true) {
            System.out.print(msg);
            String str = scanner.nextLine().trim();
            if (str.isEmpty()) {
                System.out.println("Data é obrigatória!");
                continue;
            }
            try {
                return sdf.parse(str);
            } catch (Exception e) {
                System.out.println("Formato inválido! Use dd/mm/aaaa");
            }
        }
    }

    // conversao de data para Date 
    private Date inputDateOptional(Date valorAtual) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        sdf.setLenient(false);

        while (true) {
            String str = scanner.nextLine().trim();
            if (str.isEmpty()) {
                return valorAtual; // mantem o valor atual
            }
            try {
                return sdf.parse(str);
            } catch (Exception e) {
                System.out.println("Formato inválido! Use dd/mm/aaaa ou Enter para manter");
            }
        }
    }
}