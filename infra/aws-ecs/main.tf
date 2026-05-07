locals {
  name_prefix = "${var.project}-${var.environment}"

  fhir_name     = "${local.name_prefix}-fhir"
  keycloak_name = "${local.name_prefix}-keycloak"
  tls_enabled   = length(trimspace(var.acm_certificate_arn)) > 0
}

resource "aws_cloudwatch_log_group" "fhir" {
  name              = "/ecs/${local.fhir_name}"
  retention_in_days = 30
}

resource "aws_cloudwatch_log_group" "keycloak" {
  name              = "/ecs/${local.keycloak_name}"
  retention_in_days = 30
}

resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_iam_role" "ecs_execution" {
  name = "${local.name_prefix}-ecs-exec-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution_managed" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_policy" "ecs_ssm_read" {
  name = "${local.name_prefix}-ecs-ssm-read"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ssm:GetParameter",
          "ssm:GetParameters",
          "ssm:GetParametersByPath"
        ]
        Resource = [
          var.fhir_db_pass_param_arn,
          var.kc_db_password_param_arn,
          var.kc_bootstrap_admin_password_param_arn
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "kms:Decrypt"
        ]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution_ssm" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = aws_iam_policy.ecs_ssm_read.arn
}

resource "aws_iam_role" "ecs_task" {
  name = "${local.name_prefix}-ecs-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "ecs-tasks.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })
}

resource "aws_security_group" "alb" {
  name        = "${local.name_prefix}-alb-sg"
  description = "ALB security group"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "ecs" {
  name        = "${local.name_prefix}-ecs-sg"
  description = "ECS tasks security group"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 9080
    to_port         = 9080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_lb" "main" {
  name               = "${local.name_prefix}-alb"
  load_balancer_type = "application"
  subnets            = var.public_subnet_ids
  security_groups    = [aws_security_group.alb.id]
  idle_timeout       = 120
}

resource "aws_lb_target_group" "fhir" {
  name        = "${substr(local.fhir_name, 0, 24)}-tg"
  port        = 9080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id

  health_check {
    path                = "/fhir-server/api/v4/metadata"
    protocol            = "HTTP"
    matcher             = "200-399"
    interval            = 30
    timeout             = 10
    healthy_threshold   = 2
    unhealthy_threshold = 5
  }
}

resource "aws_lb_target_group" "keycloak" {
  name        = "${substr(local.keycloak_name, 0, 24)}-tg"
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id

  health_check {
    # /admin/ returns a stable redirect/OK sequence through ALB and works
    # reliably across hostname/proxy configurations.
    path                = "/admin/"
    protocol            = "HTTP"
    matcher             = "200-399"
    interval            = 30
    timeout             = 10
    healthy_threshold   = 2
    unhealthy_threshold = 5
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = local.tls_enabled ? "redirect" : "fixed-response"

    dynamic "redirect" {
      for_each = local.tls_enabled ? [1] : []
      content {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }

    dynamic "fixed_response" {
      for_each = local.tls_enabled ? [] : [1]
      content {
        content_type = "text/plain"
        message_body = "Not found"
        status_code  = "404"
      }
    }
  }
}

resource "aws_lb_listener" "https" {
  count             = local.tls_enabled ? 1 : 0
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.acm_certificate_arn

  default_action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      message_body = "Not found"
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener_rule" "fhir_host" {
  count        = var.enable_host_routing ? 1 : 0
  listener_arn = local.tls_enabled ? aws_lb_listener.https[0].arn : aws_lb_listener.http.arn
  priority     = 100

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.fhir.arn
  }

  condition {
    host_header {
      values = [var.fhir_domain]
    }
  }
}

resource "aws_lb_listener_rule" "keycloak_host" {
  count        = var.enable_host_routing ? 1 : 0
  listener_arn = local.tls_enabled ? aws_lb_listener.https[0].arn : aws_lb_listener.http.arn
  priority     = 110

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.keycloak.arn
  }

  condition {
    host_header {
      values = [var.keycloak_domain]
    }
  }
}

resource "aws_lb_listener_rule" "fhir_path" {
  count        = var.enable_host_routing ? 0 : 1
  listener_arn = local.tls_enabled ? aws_lb_listener.https[0].arn : aws_lb_listener.http.arn
  priority     = 120

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.fhir.arn
  }

  condition {
    path_pattern {
      values = ["/fhir-server/*", "/fhir-server"]
    }
  }
}

resource "aws_lb_listener_rule" "keycloak_path" {
  count        = var.enable_host_routing ? 0 : 1
  listener_arn = local.tls_enabled ? aws_lb_listener.https[0].arn : aws_lb_listener.http.arn
  priority     = 130

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.keycloak.arn
  }

  condition {
    path_pattern {
      values = ["/realms/*", "/admin/*", "/resources/*", "/protocol/*"]
    }
  }
}

resource "aws_ecs_task_definition" "fhir" {
  family                   = local.fhir_name
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = tostring(var.fhir_cpu)
  memory                   = tostring(var.fhir_memory)
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  runtime_platform {
    cpu_architecture        = "ARM64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([
    {
      name      = "fhir"
      image     = var.fhir_image
      essential = true
      portMappings = [
        {
          containerPort = 9080
          protocol      = "tcp"
        }
      ]
      environment = [
        { name = "SMART_CLIENT_ID", value = var.smart_client_id },
        { name = "FHIR_DB_HOST", value = var.fhir_db_host },
        { name = "FHIR_DB_PORT", value = tostring(var.fhir_db_port) },
        { name = "FHIR_DB_NAME", value = var.fhir_db_name },
        { name = "FHIR_DB_SCHEMA", value = var.fhir_db_schema },
        { name = "FHIR_DB_USER", value = var.fhir_db_user },
        { name = "FHIR_DB_SSL_MODE", value = var.fhir_db_ssl_mode },
        { name = "ABAC_ENABLED", value = var.abac_enabled },
        { name = "ABAC_REQUIRE_TENANT", value = var.abac_require_tenant },
        { name = "ABAC_REQUIRE_ORG", value = var.abac_require_org },
        { name = "ABAC_ALLOWED_PURPOSES", value = var.abac_allowed_purposes },
        { name = "ABAC_RESOURCE_TENANT_SYSTEM", value = var.abac_resource_tenant_system },
        { name = "ABAC_RESOURCE_ORG_SYSTEM", value = var.abac_resource_org_system }
      ]
      secrets = [
        { name = "FHIR_DB_PASS", valueFrom = var.fhir_db_pass_param_arn }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-region        = var.aws_region
          awslogs-group         = aws_cloudwatch_log_group.fhir.name
          awslogs-stream-prefix = "ecs"
        }
      }
      healthCheck = {
        command     = ["CMD-SHELL", "curl -sf http://localhost:9080/fhir-server/api/v4/metadata || exit 1"]
        interval    = 30
        timeout     = 10
        retries     = 5
        startPeriod = 120
      }
    }
  ])
}

resource "aws_ecs_task_definition" "keycloak" {
  family                   = local.keycloak_name
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = tostring(var.keycloak_cpu)
  memory                   = tostring(var.keycloak_memory)
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  runtime_platform {
    cpu_architecture        = "ARM64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([
    {
      name      = "keycloak"
      image     = var.keycloak_image
      essential = true
      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]
      environment = [
        { name = "KC_BOOTSTRAP_ADMIN_USERNAME", value = var.kc_bootstrap_admin_username },
        { name = "SMART_REALM", value = var.smart_realm },
        { name = "FHIR_BASE_URL", value = var.fhir_base_url },
        { name = "INTERNAL_FHIR_URL", value = var.internal_fhir_url },
        { name = "SMART_CLIENT_ID", value = var.smart_client_id },
        { name = "SMART_CLIENT_REDIRECT_URI", value = var.smart_client_redirect_uri },
        { name = "KC_DB", value = "postgres" },
        { name = "KC_DB_SCHEMA", value = var.kc_db_schema },
        { name = "KC_DB_URL", value = "jdbc:postgresql://${var.kc_db_host}:${var.kc_db_port}/${var.kc_db_database}?sslmode=${var.kc_db_ssl_mode}" },
        { name = "KC_DB_USERNAME", value = var.kc_db_username },
        { name = "KC_PROXY_HEADERS", value = "xforwarded" },
        { name = "KC_HTTP_ENABLED", value = "true" },
        { name = "KC_HOSTNAME_STRICT", value = "false" },
        { name = "KC_HOSTNAME", value = var.keycloak_hostname }
      ]
      secrets = [
        { name = "KC_BOOTSTRAP_ADMIN_PASSWORD", valueFrom = var.kc_bootstrap_admin_password_param_arn },
        { name = "KC_DB_PASSWORD", valueFrom = var.kc_db_password_param_arn }
      ]
      command = [
        "start",
        "--optimized",
        "--import-realm",
        "--spi-connections-jpa-quarkus-migration-strategy=update"
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-region        = var.aws_region
          awslogs-group         = aws_cloudwatch_log_group.keycloak.name
          awslogs-stream-prefix = "ecs"
        }
      }
      healthCheck = {
        command     = ["CMD-SHELL", "curl -fsS http://localhost:8080/realms/master >/dev/null || exit 1"]
        interval    = 30
        timeout     = 10
        retries     = 5
        startPeriod = 120
      }
    }
  ])
}

resource "aws_ecs_service" "fhir" {
  name            = local.fhir_name
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.fhir.arn
  desired_count   = var.fhir_min_capacity

  network_configuration {
    subnets          = var.task_subnet_ids
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = var.task_assign_public_ip
  }

  capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = var.fargate_weight
  }

  capacity_provider_strategy {
    capacity_provider = "FARGATE_SPOT"
    weight            = var.fargate_spot_weight_fhir
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.fhir.arn
    container_name   = "fhir"
    container_port   = 9080
  }

  depends_on = [aws_lb_listener.http]
}

resource "aws_ecs_service" "keycloak" {
  name            = local.keycloak_name
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.keycloak.arn
  desired_count   = var.keycloak_min_capacity

  network_configuration {
    subnets          = var.task_subnet_ids
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = var.task_assign_public_ip
  }

  capacity_provider_strategy {
    capacity_provider = "FARGATE"
    base              = 1
    weight            = var.fargate_weight
  }

  capacity_provider_strategy {
    capacity_provider = "FARGATE_SPOT"
    weight            = var.fargate_spot_weight_keycloak
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.keycloak.arn
    container_name   = "keycloak"
    container_port   = 8080
  }

  depends_on = [aws_lb_listener.http]
}

resource "aws_appautoscaling_target" "fhir" {
  max_capacity       = var.fhir_max_capacity
  min_capacity       = var.fhir_min_capacity
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.fhir.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "fhir_cpu" {
  name               = "${local.fhir_name}-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.fhir.resource_id
  scalable_dimension = aws_appautoscaling_target.fhir.scalable_dimension
  service_namespace  = aws_appautoscaling_target.fhir.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }

    target_value       = var.fhir_target_cpu
    scale_in_cooldown  = 180
    scale_out_cooldown = 60
  }
}

resource "aws_appautoscaling_target" "keycloak" {
  max_capacity       = var.keycloak_max_capacity
  min_capacity       = var.keycloak_min_capacity
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.keycloak.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "keycloak_cpu" {
  name               = "${local.keycloak_name}-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.keycloak.resource_id
  scalable_dimension = aws_appautoscaling_target.keycloak.scalable_dimension
  service_namespace  = aws_appautoscaling_target.keycloak.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }

    target_value       = var.keycloak_target_cpu
    scale_in_cooldown  = 180
    scale_out_cooldown = 60
  }
}
