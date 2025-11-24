import com.mongodb.client.*;
import org.bson.Document;
import java.util.Scanner;
import java.util.Arrays;

public class Relatorios {
    private MongoDatabase database;
    private Scanner scanner;
    
    public Relatorios(MongoDatabase database, Scanner scanner) {
        this.database = database;
        this.scanner  = scanner;
    }
    
    public void menu() {
        while (true) {
            System.out.println("\n=== RELATÓRIOS ===");
            System.out.println("1. Número de clientes por plano");
            System.out.println("2. Manutenções pendentes");
            System.out.println("3. Funcionários ativos por cargo");
            System.out.println("4. Voltar");
            System.out.print("Escolha: ");
            
            int opcao = scanner.nextInt();
            scanner.nextLine();
            
            switch (opcao) {
                case 1  -> clientesPorPlano();
                case 2  -> manutencoesPendentes();
                case 3  -> funcionariosAtivosPorCargo();
                case 4  -> { return; }
                default -> System.out.println("Opção inválida!");
            }
        }
    }
    
    private void clientesPorPlano() {
        MongoCollection<Document> planos = database.getCollection("planos");
        MongoCollection<Document> clientes = database.getCollection("clientes");
        
        if (planos.countDocuments() == 0) {
            System.out.println("\n--- CLIENTES POR PLANO ---");
            System.out.println("Nenhum plano cadastrado.");
            return;
        }
        
        if (clientes.countDocuments() == 0) {
            System.out.println("\n--- CLIENTES POR PLANO ---");
            System.out.println("Nenhum cliente cadastrado.");
            return;
        }
        
        AggregateIterable<Document> result = planos.aggregate(Arrays.asList(
            new Document("$lookup", new Document()
                .append("from", "clientes")
                .append("localField", "_id")
                .append("foreignField", "id_plano")
                .append("as", "clientes")),
            new Document("$project", new Document()
                .append("plano", "$nome")
                .append("quantidade", new Document("$size", "$clientes")))
        ));
        
        System.out.println("\n--- CLIENTES POR PLANO ---");
        boolean encontrouClientes = false;
        for (Document doc : result) {
            int quantidade = doc.getInteger("quantidade");
            if (quantidade > 0) {
                encontrouClientes = true;
                System.out.printf("Plano: %s | Clientes: %d\n",
                    doc.getString("plano"),
                    quantidade);
            }
        }
        
        if (!encontrouClientes) {
            System.out.println("Nenhum cliente vinculado aos planos existentes.");
        }
    }
    
    private void manutencoesPendentes() {
        MongoCollection<Document> manutencoes = database.getCollection("manutencoes");
        MongoCollection<Document> equipamentos = database.getCollection("equipamentos");
        
        if (manutencoes.countDocuments() == 0) {
            System.out.println("\n--- MANUTENÇÕES PENDENTES ---");
            System.out.println("Nenhuma manutenção cadastrada.");
            return;
        }
        
        if (equipamentos.countDocuments() == 0) {
            System.out.println("\n--- MANUTENÇÕES PENDENTES ---");
            System.out.println("Nenhum equipamento cadastrado.");
            return;
        }
        
        AggregateIterable<Document> result = manutencoes.aggregate(Arrays.asList(
            new Document("$match", new Document("status", 0)),
            new Document("$lookup", new Document()
                .append("from", "equipamentos")
                .append("localField", "id_equipamento")
                .append("foreignField", "_id")
                .append("as", "equipamento")),
            new Document("$unwind", "$equipamento"),
            new Document("$project", new Document()
                .append("modelo", "$equipamento.modelo")
                .append("data_prevista", "$data_prevista")
                .append("custo", "$custo"))
        ));
        
        System.out.println("\n--- MANUTENÇÕES PENDENTES ---");
        boolean encontrouPendentes = false;
        for (Document doc : result) {
            encontrouPendentes = true;
            System.out.printf("Equipamento: %s | Data Prevista: %s | Custo: R$%.2f\n",
                doc.getString("modelo"),
                doc.getDate("data_prevista"),
                doc.getDouble("custo"));
        }
        
        if (!encontrouPendentes) {
            System.out.println("Nenhuma manutenção pendente encontrada.");
        }
    }
    
    private void funcionariosAtivosPorCargo() {
        MongoCollection<Document> cargos = database.getCollection("cargos");
        MongoCollection<Document> funcionarios = database.getCollection("funcionarios");
        
        if (cargos.countDocuments() == 0) {
            System.out.println("\n--- FUNCIONÁRIOS ATIVOS POR CARGO ---");
            System.out.println("Nenhum cargo cadastrado.");
            return;
        }
        
        if (funcionarios.countDocuments() == 0) {
            System.out.println("\n--- FUNCIONÁRIOS ATIVOS POR CARGO ---");
            System.out.println("Nenhum funcionário cadastrado.");
            return;
        }
        
        AggregateIterable<Document> result = cargos.aggregate(Arrays.asList(
            new Document("$lookup", new Document()
                .append("from", "funcionarios")
                .append("let", new Document("id_cargo", "$_id"))
                .append("pipeline", Arrays.asList(
                    new Document("$match", new Document("$expr",
                        new Document("$and", Arrays.asList(
                            new Document("$eq", Arrays.asList("$id_cargo", "$$id_cargo")),
                            new Document("$eq", Arrays.asList("$status", 1))
                        ))
                    ))
                ))
                .append("as", "funcionarios")),
            new Document("$project", new Document()
                .append("cargo", "$nome")
                .append("quantidade", new Document("$size", "$funcionarios")))
        ));
        
        System.out.println("\n--- FUNCIONÁRIOS ATIVOS POR CARGO ---");
        boolean encontrouAtivos = false;
        for (Document doc : result) {
            int quantidade = doc.getInteger("quantidade");
            if (quantidade > 0) {
                encontrouAtivos = true;
                System.out.printf("Cargo: %s | Funcionários Ativos: %d\n",
                    doc.getString("cargo"),
                    quantidade);
            }
        }
        
        if (!encontrouAtivos) {
            System.out.println("Nenhum funcionário ativo encontrado nos cargos existentes.");
        }
    }
}